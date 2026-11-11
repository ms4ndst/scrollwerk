package dev.scrollwerk.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    private fun settings(q: String) = SettingsSearch.search(q).map { it.item.label }

    @Test fun settingsMatchLabelsFuzzily() {
        assertEquals("Wi-Fi", settings("wifi").first())
        assertEquals("Bluetooth", settings("blue").first())
        assertEquals("Developer options", settings("dev").first())
    }

    @Test fun settingsMatchKeywordsWithoutHighlight() {
        val r = SettingsSearch.search("brightness")
        assertEquals("Display", r.first().item.label)
        assertTrue(r.first().matched.isEmpty())
    }

    @Test fun settingsNeedTwoCharactersAndAreCapped() {
        assertTrue(SettingsSearch.search("w").isEmpty())
        assertTrue(SettingsSearch.search("s").size <= SettingsSearch.LIMIT)
        assertTrue(SettingsSearch.search("se").size <= SettingsSearch.LIMIT)
    }

    @Test fun everySettingsActionIsAnAndroidAction() {
        assertTrue(SettingsSearch.pages.all { it.action.startsWith("android.settings.") || it.action.startsWith("android.intent.action.") })
        assertEquals(SettingsSearch.pages.size, SettingsSearch.pages.map { it.label }.distinct().size)
    }

    private val anna = ContactEntry(1, "Anna Berg", "content://contacts/1", true)
    private val bo = ContactEntry(2, "Bo Andersson", "content://contacts/2", false)
    private val annika = ContactEntry(3, "Annika Lund", "content://contacts/3", true)

    @Test fun contactsMatchFuzzilyBestFirst() {
        val r = ContactSearch.search(listOf(bo, annika, anna), "anna")
        assertEquals("Anna Berg", r.first().item.name)
        assertEquals(listOf(0, 1, 2, 3), r.first().matched.toList())
        assertTrue(ContactSearch.search(listOf(anna), "a").isEmpty())
    }

    @Test fun webSearchEncodesTheQuery() {
        assertEquals("https://duckduckgo.com/?q=r%C3%A4ksm%C3%B6rg%C3%A5s+recept", WebSearch.url("https://duckduckgo.com/?q=%s", " räksmörgås recept "))
    }
}
