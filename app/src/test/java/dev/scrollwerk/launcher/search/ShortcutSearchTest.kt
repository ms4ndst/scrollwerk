package dev.scrollwerk.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutSearchTest {
    private val gmail = "com.google.android.gm/.Main#0"
    private val slack = "com.Slack/.Home#0"
    private val labels = mapOf(gmail to "Gmail", slack to "Slack")
    private fun sc(app: String, label: String) = ShortcutEntry("$app:$label", app, app.substringBefore('/'), label)

    private val compose = sc(gmail, "Compose")
    private val dnd = sc(slack, "Do not disturb")
    private val mentions = sc(slack, "Mentions")
    private val all = listOf(compose, dnd, mentions)

    private fun labels(r: List<ShortcutResult>) = r.map { it.shortcut.label }

    @Test fun matchesTheShortcutsOwnLabelWithHighlights() {
        val r = ShortcutSearch.search(all, labels, "comp")
        assertEquals(listOf("Compose"), labels(r))
        assertEquals(listOf(0, 1, 2, 3), r[0].matched.toList())
        assertEquals("Gmail", r[0].appLabel)
    }

    @Test fun typingTheAppNameListsItsShortcuts() {
        assertEquals(setOf("Do not disturb", "Mentions"), labels(ShortcutSearch.search(all, labels, "slack")).toSet())
    }

    @Test fun ownLabelMatchesRankAboveAppNameMatches() {
        val mail = sc(slack, "Mail digest")
        val r = ShortcutSearch.search(all + mail, labels, "mail")
        assertEquals("Mail digest", r.first().shortcut.label) // its own label beats Gmail's app name
    }

    @Test fun tooShortOrOrphanedGivesNothing() {
        assertTrue(ShortcutSearch.search(all, labels, "c").isEmpty())
        assertTrue(ShortcutSearch.search(listOf(sc("gone/.X#0", "Compose")), labels, "compose").isEmpty())
    }

    @Test fun resultsAreCapped() {
        val many = (1..10).map { sc(gmail, "Label $it") }
        assertEquals(ShortcutSearch.LIMIT, ShortcutSearch.search(many, labels, "label").size)
    }
}
