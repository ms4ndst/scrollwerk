package dev.scrollwerk.launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDigestTest {
    private val utc = java.time.ZoneOffset.UTC
    // Wed 18 Nov 2026, 12:40 UTC
    private val now = java.time.ZonedDateTime.of(2026, 11, 18, 12, 40, 0, 0, utc).toInstant().toEpochMilli()

    @Test fun listsEachAlertOnOneLine() {
        val prompt = AlertDigest.prompt(
            listOf(
                DigestItem("WhatsApp", "Anna", "Are we still on\nfor dinner?", now - 5 * 60_000),
                DigestItem("Email", null, "Your invoice is ready", now - 2 * 86_400_000),
                DigestItem("Slack", "#team", null, now - 10 * 86_400_000),
            ),
            now, utc,
        )
        val lines = prompt.lines()
        assertTrue(lines.first().startsWith("Here are my unread notifications"))
        assertEquals(
            listOf(
                "- WhatsApp · Anna · 12:35: Are we still on for dinner?",
                "- Email · Mon 12:40: Your invoice is ready",
                "- Slack · #team · 8 Nov 12:40",
            ),
            lines.takeLast(3),
        )
    }

    @Test fun longTextsAreCutAndManyAlertsCapped() {
        val items = List(AlertDigest.MAX_ITEMS + 5) { DigestItem("SMS", "n$it", "x".repeat(1000), now) }
        val prompt = AlertDigest.prompt(items, now, utc)
        assertEquals(AlertDigest.MAX_ITEMS, prompt.lines().count { it.startsWith("- SMS") })
        assertTrue(prompt.lines().first { it.startsWith("- SMS") }.endsWith("…"))
        assertTrue(prompt.endsWith("- …and 5 older notifications not listed."))
    }

    @Test fun localMeansThisPhone() {
        assertTrue(ChatProtocol.isLocal("http://127.0.0.1:8080"))
        assertTrue(ChatProtocol.isLocal("http://localhost:11434/"))
        assertFalse(ChatProtocol.isLocal("https://ai.example.com"))
        assertFalse(ChatProtocol.isLocal("http://127.0.0.1.example.com"))
    }
}

class AlertDigestContextTest {
    private val utc = java.time.ZoneOffset.UTC

    @Test fun contextListsTheAlertsAndIsAbsentWithoutAny() {
        val now = java.time.ZonedDateTime.of(2026, 11, 18, 12, 41, 0, 0, utc).toInstant().toEpochMilli()
        org.junit.Assert.assertNull(AlertDigest.context(emptyList(), now, utc))
        val items = listOf(DigestItem("SMS", "Magnus Sandström", "Test", now - 60_000))
        val ctx = AlertDigest.context(items, now, utc)!!
        assertTrue(ctx.startsWith("The user's unread notifications"))
        assertEquals("- SMS · Magnus Sandström · 12:40: Test", ctx.lines().last())
        // A minute later the text is the same, so the AI app can reuse what it already read.
        assertEquals(ctx, AlertDigest.context(items, now + 60_000, utc))
    }
}
