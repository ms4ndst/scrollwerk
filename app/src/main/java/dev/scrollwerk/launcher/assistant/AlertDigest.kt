package dev.scrollwerk.launcher.assistant

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One alert as the digest sees it: no intents or keys, just what was said, by whom, and when. */
data class DigestItem(val source: String, val title: String?, val text: String?, val time: Long)

/**
 * "What did I miss?": turns the notification bar's alerts into one question for the assistant.
 * Pure, so the exact text the model gets is unit-tested.
 */
object AlertDigest {
    const val QUESTION = "What did I miss?"
    const val MAX_ITEMS = 40
    const val MAX_TEXT = 400

    /**
     * Times are clock times ("14:05", "Mon 09:12"), not "5 min ago": the list then reads the same from one
     * question to the next, so the AI app can reuse what it already read and answer follow-ups sooner.
     */
    fun prompt(items: List<DigestItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine(
            "Here are my unread notifications, newest first. Summarise what I missed in a few short lines: " +
                "group them by person or conversation, say who wants what, and point out anything that looks urgent " +
                "or needs a reply from me. Use only what's in them; don't guess or add anything.",
        )
        appendLine("It's now ${clock(now, now, zone)}.")
        appendLine()
        append(list(items, now, zone))
    }.trimEnd()

    /**
     * Background for every question while the AI app is on this phone, so typed questions
     * ("what did I miss?", "what did Anna want?") work as well as the chip.
     */
    fun context(items: List<DigestItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (items.isEmpty()) return null
        return (
            "The user's unread notifications on this phone right now, newest first. Use them only when the user asks " +
                "about messages, notifications, people in them or what they missed; then use only what's listed here.\n" +
                list(items, now, zone)
            ).trimEnd()
    }

    private fun list(items: List<DigestItem>, now: Long, zone: ZoneId): String = buildString {
        items.take(MAX_ITEMS).forEach { item ->
            append("- ").append(item.source)
            item.title?.let { append(" · ").append(oneLine(it, 80)) }
            append(" · ").append(clock(item.time, now, zone))
            item.text?.let { append(": ").append(oneLine(it, MAX_TEXT)) }
            appendLine()
        }
        if (items.size > MAX_ITEMS) appendLine("- …and ${items.size - MAX_ITEMS} older notifications not listed.")
    }

    private fun oneLine(s: String, max: Int): String {
        val flat = s.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
    }

    /** "14:05" today, "Mon 09:12" this past week, else "12 Nov 09:12". */
    private fun clock(time: Long, now: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(time).atZone(zone)
        val days = ChronoUnit.DAYS.between(t.toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        val pattern = when {
            days <= 0 -> "HH:mm"
            days < 7 -> "EEE HH:mm"
            else -> "d MMM HH:mm"
        }
        return t.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }
}
