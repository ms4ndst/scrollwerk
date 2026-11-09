package dev.scrollwerk.launcher.config

import dev.scrollwerk.launcher.data.UsageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun defaultsRoundTrip() {
        val defaults = LauncherConfig()
        assertEquals(defaults, LauncherConfig.parse(LauncherConfig.encode(defaults)).getOrThrow())
    }

    @Test fun partialFileWithCommentsAndUnknownKeysParses() {
        val text = """
            {
              // only override what you care about
              "theme": { "palette": "Catppuccin", "mode": "light", },
              "launcher": { "searchPosition": "top" },
              "futureFeature": true,
            }
        """.trimIndent()
        val config = LauncherConfig.parse(text).getOrThrow()
        assertEquals("Catppuccin", config.theme.palette)
        assertEquals(ThemeMode.Light, config.theme.mode)
        assertEquals(SearchPosition.Top, config.launcher.searchPosition)
        assertTrue(config.bar.floating)
    }

    @Test fun calendarAppRoundTripsAndDefaultsToSystem() {
        assertEquals(null, LauncherConfig().widgets.calendarApp)
        val chosen = LauncherConfig().let { it.copy(widgets = it.widgets.copy(calendarApp = "ws.xsoh.etar")) }
        assertEquals("ws.xsoh.etar", LauncherConfig.parse(LauncherConfig.encode(chosen)).getOrThrow().widgets.calendarApp)
    }

    @Test fun missingFileIsCreatedWithDefaults() {
        val file = tmp.root.resolve("sub/config.json")
        assertEquals(LauncherConfig(), ConfigStore(file).load().config)
        assertTrue(file.exists())
    }

    @Test fun brokenFileKeepsLastGoodConfigAndReportsError() {
        val file = tmp.newFile("config.json").apply { writeText("{ \"bar\": ") }
        val lastGood = LauncherConfig(bar = LauncherConfig.Bar(floating = false))
        val loaded = ConfigStore(file).load(lastGood)
        assertEquals(lastGood, loaded.config)
        assertNotNull(loaded.error)
    }

    @Test fun usageStateTracksRecentsAndPins() {
        val s = UsageState().launched("a").launched("b").launched("a").togglePin("b")
        assertEquals(listOf("a", "b"), s.recent)
        assertEquals(2, s.count("a"))
        assertEquals(listOf("b"), s.pinned)
        assertEquals(emptyList<String>(), s.togglePin("b").pinned)
    }
}
