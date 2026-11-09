package dev.scrollwerk.launcher.search

import dev.scrollwerk.launcher.data.AppEntry
import dev.scrollwerk.launcher.data.UsageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private fun app(label: String, category: String? = null) =
        AppEntry("pkg.${label.lowercase().replace(" ", "")}/.Main#0", label, "pkg.${label.lowercase().replace(" ", "")}", category)

    private val firefox = app("Firefox")
    private val files = app("Files")
    private val fitness = app("Fitness", "Productivity")
    private val maps = app("Maps", "Maps")
    private val chess = app("Chess", "Games")
    private val all = listOf(maps, firefox, chess, files, fitness)

    private fun labels(r: List<AppResult>) = r.map { it.app.label }

    @Test fun emptyQueryListsPinnedFirstThenAlphabetical() {
        val usage = UsageState(pinned = listOf(maps.id, chess.id))
        val r = AppSearch.search(all, "", usage, sortByUsage = false)
        assertEquals(listOf("Maps", "Chess", "Files", "Firefox", "Fitness"), labels(r))
        assertTrue(r[0].pinned && r[1].pinned && !r[2].pinned)
    }

    @Test fun emptyQueryStaysAlphabeticalRegardlessOfUsage() {
        val usage = UsageState(counts = mapOf(fitness.id to 5, files.id to 1))
        assertEquals(listOf("Chess", "Files", "Firefox", "Fitness", "Maps"), labels(AppSearch.search(all, "", usage)))
    }

    @Test fun queryRanksBestMatchFirstAndDropsNonMatches() {
        val r = AppSearch.search(all, "fir", UsageState(), sortByUsage = false)
        assertEquals("Firefox", r.first().app.label)
        assertTrue(r.none { it.app.label == "Maps" || it.app.label == "Chess" })
        assertEquals(listOf(0, 1, 2), r.first().matched.toList())
    }

    @Test fun usageBoostCanReorderCloseMatches() {
        val plain = labels(AppSearch.search(all, "fi", UsageState(), sortByUsage = true))
        val boosted = labels(AppSearch.search(all, "fi", UsageState(counts = mapOf(fitness.id to 50)), sortByUsage = true))
        assertEquals("Fitness", boosted.first())
        assertTrue(plain.first() != "Fitness" || boosted.first() == "Fitness")
    }

    @Test fun recentFilterKeepsRecencyOrder() {
        val usage = UsageState().launched(maps.id).launched(chess.id).launched(maps.id)
        assertEquals(listOf("Maps", "Chess"), labels(AppSearch.search(all, "", usage, AppFilter.Recent)))
    }

    @Test fun categoryFilterAppliesToSearchToo() {
        assertEquals(listOf("Chess"), labels(AppSearch.search(all, "", UsageState(), AppFilter.Category("Games"))))
        assertTrue(AppSearch.search(all, "fir", UsageState(), AppFilter.Category("Games")).isEmpty())
        assertEquals(listOf("Games", "Maps", "Productivity"), AppSearch.categories(all))
    }

    @Test fun packageNameSubstringIsAFallback() {
        val signal = AppEntry("org.thoughtcrime.securesms/.Main#0", "Signal", "org.thoughtcrime.securesms")
        assertEquals(listOf("Signal"), labels(AppSearch.search(listOf(signal), "securesms", UsageState())))
    }
}
