package dev.scrollwerk.launcher.assistant

import dev.scrollwerk.launcher.assistant.ChatProtocol.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProtocolTest {
    @Test fun endpointsJoinToV1Paths() {
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", ChatProtocol.url("http://127.0.0.1:8080", "chat/completions"))
        assertEquals("http://127.0.0.1:11434/v1/models", ChatProtocol.url(" http://127.0.0.1:11434/v1/ ", "/models"))
    }

    @Test fun plainHttpOnlyToThePhoneItself() {
        assertTrue(ChatProtocol.isAllowed("http://127.0.0.1:8080"))
        assertTrue(ChatProtocol.isAllowed("http://localhost:11434/v1"))
        assertTrue(ChatProtocol.isAllowed("https://ai.example.com"))
        assertFalse(ChatProtocol.isAllowed("http://192.168.1.20:8080"))
        assertFalse(ChatProtocol.isAllowed("http://127.0.0.1.evil.com"))
        assertFalse(ChatProtocol.isAllowed("ftp://localhost"))
    }

    @Test fun requestCarriesModelStreamAndMessages() {
        val body = ChatProtocol.request("gemma3:1b", listOf(
            ChatMessage(ChatMessage.Role.System, "Be brief."),
            ChatMessage(ChatMessage.Role.User, "Hej \"där\""),
        ))
        assertEquals("""{"model":"gemma3:1b","stream":true,"messages":[{"role":"system","content":"Be brief."},{"role":"user","content":"Hej \"där\""}]}""", body)
        assertFalse(ChatProtocol.request(null, emptyList()).contains("model"))
    }

    @Test fun streamLinesBecomeDeltasDoneOrNothing() {
        assertEquals(StreamEvent.Delta("Hel"), ChatProtocol.parseStreamLine("""data: {"choices":[{"delta":{"content":"Hel"}}]}"""))
        assertEquals(StreamEvent.Done, ChatProtocol.parseStreamLine("data: [DONE]"))
        assertNull(ChatProtocol.parseStreamLine(""))
        assertNull(ChatProtocol.parseStreamLine(": keep-alive"))
        assertNull(ChatProtocol.parseStreamLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertEquals(StreamEvent.Error("model not found"), ChatProtocol.parseStreamLine("""data: {"error":{"message":"model not found"}}"""))
    }

    @Test fun modelsAndErrorsAreRead() {
        assertEquals(listOf("qwen2.5:7b", "gemma3:1b"), ChatProtocol.parseModels("""{"object":"list","data":[{"id":"qwen2.5:7b"},{"id":"gemma3:1b"}]}"""))
        assertEquals(emptyList<String>(), ChatProtocol.parseModels("not json"))
        assertEquals("boom", ChatProtocol.errorMessage("""{"error":"boom"}"""))
    }

    @Test fun thinkingIsHiddenEvenWhileStreaming() {
        assertEquals("Answer.", ChatProtocol.visibleText("<think>hmm, let me see</think>\n\nAnswer."))
        assertEquals("", ChatProtocol.visibleText("<think>still thinking"))
        assertTrue(ChatProtocol.isThinking("<think>still thinking"))
        assertFalse(ChatProtocol.isThinking("<think>done</think>Hi"))
    }
}
