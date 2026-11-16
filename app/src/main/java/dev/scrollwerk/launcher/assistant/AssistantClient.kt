package dev.scrollwerk.launcher.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Talks to a local AI runner (llama.cpp's server, Ollama, …) over its OpenAI-compatible API. */
object AssistantClient {
    private const val CONNECT_MS = 4_000
    /** Generous: a model can take a while to load on the first question. */
    private const val READ_MS = 180_000

    class Failure(message: String) : IOException(message)

    /** The models the runner offers; a failure says what to do about it. */
    suspend fun models(endpoint: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(endpoint, "models", "GET")
            try {
                if (conn.responseCode !in 200..299) throw Failure(httpError(conn))
                ChatProtocol.parseModels(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { throw explain(it, endpoint) }
    }

    /** The answer as it's written, piece by piece. Cancel the collection to stop it. */
    fun chat(endpoint: String, model: String?, messages: List<ChatMessage>): Flow<String> = flow {
        val conn = try {
            open(endpoint, "chat/completions", "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                outputStream.use { it.write(ChatProtocol.request(model, messages).toByteArray()) }
            }
        } catch (e: Exception) {
            throw explain(e, endpoint)
        }
        try {
            if (conn.responseCode !in 200..299) throw Failure(httpError(conn))
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    when (val event = ChatProtocol.parseStreamLine(line)) {
                        is ChatProtocol.StreamEvent.Delta -> emit(event.text)
                        is ChatProtocol.StreamEvent.Error -> throw Failure(event.message)
                        ChatProtocol.StreamEvent.Done -> return@useLines
                        null -> Unit
                    }
                }
            }
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw explain(e, endpoint)
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun open(endpoint: String, path: String, method: String): HttpURLConnection {
        if (!ChatProtocol.isAllowed(endpoint)) {
            throw Failure("Scrollwerk only talks plain HTTP to the phone itself (127.0.0.1 or localhost). Use one of those, or an https:// address.")
        }
        return (URL(ChatProtocol.url(endpoint, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
        }
    }

    private fun httpError(conn: HttpURLConnection): String {
        val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        return ChatProtocol.errorMessage(body) ?: "The AI app answered with error ${conn.responseCode}."
    }

    private fun explain(e: Throwable, endpoint: String): Throwable = when (e) {
        is Failure -> e
        is ConnectException -> Failure("Nothing is answering at $endpoint. Start your local AI app, or check the address in the Control Center.")
        is SocketTimeoutException -> Failure("The AI app at $endpoint didn't answer in time. It may still be loading the model; try again.")
        else -> Failure(e.message ?: "Couldn't reach the AI app at $endpoint.")
    }
}
