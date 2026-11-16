package dev.scrollwerk.launcher.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** One turn of the conversation, in the OpenAI-style chat format local runners speak. */
data class ChatMessage(val role: Role, val content: String) {
    enum class Role(val wire: String) { System("system"), User("user"), Assistant("assistant") }
}

/**
 * The OpenAI-compatible chat API that llama.cpp's server, Ollama, LM Studio and most local runners
 * offer under `/v1`. Pure functions only: building requests and reading the streamed answer.
 */
object ChatProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** "http://127.0.0.1:8080", "…:8080/" or "…:8080/v1" all become ".../v1/[path]". */
    fun url(endpoint: String, path: String): String {
        val base = endpoint.trim().trimEnd('/').removeSuffix("/v1")
        return "$base/v1/${path.trimStart('/')}"
    }

    /** Plain HTTP only to the phone itself; anything else must be HTTPS. Keeps prompts on the phone by default. */
    fun isAllowed(endpoint: String): Boolean {
        val e = endpoint.trim().lowercase()
        if (e.startsWith("https://")) return true
        val host = e.removePrefix("http://").substringBefore('/').substringBefore(':')
        return e.startsWith("http://") && host in setOf("127.0.0.1", "localhost")
    }

    fun request(model: String?, messages: List<ChatMessage>, stream: Boolean = true): String = buildJsonObject {
        model?.let { put("model", it) }
        put("stream", stream)
        put("messages", buildJsonArray {
            messages.forEach { m -> add(buildJsonObject { put("role", m.role.wire); put("content", m.content) }) }
        })
    }.toString()

    /** What one line of the streamed answer means. */
    sealed interface StreamEvent {
        data class Delta(val text: String) : StreamEvent
        data object Done : StreamEvent
        data class Error(val message: String) : StreamEvent
    }

    /** Reads one server-sent-events line ("data: {…}"); blank lines, comments and keep-alives give null. */
    fun parseStreamLine(line: String): StreamEvent? {
        val t = line.trim()
        if (!t.startsWith("data:")) return null
        val payload = t.removePrefix("data:").trim()
        if (payload == "[DONE]") return StreamEvent.Done
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        errorMessage(obj)?.let { return StreamEvent.Error(it) }
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val delta = choice["delta"] as? JsonObject
        val text = (delta?.get("content") as? JsonPrimitive)?.contentOrNull
            ?: ((choice["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
        return if (text.isNullOrEmpty()) null else StreamEvent.Delta(text)
    }

    /** Model ids from `/v1/models`. */
    fun parseModels(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
            .mapNotNull { (it.jsonObject["id"] as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList())

    /** The server's error text from an error body, if it gave one. */
    fun errorMessage(body: String): String? =
        runCatching { errorMessage(json.parseToJsonElement(body).jsonObject) }.getOrNull()

    private fun errorMessage(obj: JsonObject): String? = when (val e = obj["error"]) {
        is JsonPrimitive -> e.contentOrNull
        is JsonObject -> (e["message"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    /**
     * Reasoning models (Qwen 3, DeepSeek R1 …) think inside <think>…</think> before answering. Hide that part,
     * including a block that's still open while it streams.
     */
    fun visibleText(raw: String): String {
        var text = raw.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.trimStart()
    }

    /** True while a reasoning model is still inside its <think> block. */
    fun isThinking(raw: String): Boolean = raw.lastIndexOf("<think>") > raw.lastIndexOf("</think>")
}
