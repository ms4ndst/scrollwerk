package dev.scrollwerk.launcher.ui

import dev.scrollwerk.launcher.ui.theme.Japanese
import dev.scrollwerk.launcher.ui.theme.ThemeExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JapaneseTest {
    @Test fun kanjiNumerals() {
        assertEquals(listOf("一", "二", "九", "十", "十一", "十九", "二十", "二十三", "九十九"),
            listOf(1, 2, 9, 10, 11, 19, 20, 23, 99).map(Japanese::numeral))
        assertEquals("〇", Japanese.numeral(0))
        assertEquals("100", Japanese.numeral(100))
    }

    @Test fun onlyKanagawaHasExtras() {
        assertTrue(ThemeExtras.forPalette("kanagawa").kanji)
        assertEquals(ThemeExtras.NONE, ThemeExtras.forPalette("Nord"))
        assertEquals(ThemeExtras.NONE, ThemeExtras.forPalette(null))
    }

    /** Every character Japanese can write must be in the bundled kanji subset (tools/gen_fonts.py KANJI). */
    @Test fun kanjiFontSubsetCoversEverythingWeWrite() {
        val script = listOf(File("../tools/gen_fonts.py"), File("tools/gen_fonts.py")).first { it.exists() }.readText()
        val bundled = Regex("""KANJI = \[ord\(ch\) for ch in "([^"]+)"\]""").find(script)!!.groupValues[1]
        val written = (0..99).joinToString("") { Japanese.numeral(it) }
        val missing = written.toSet().filter { it !in bundled }
        assertTrue("not in the kanji font: $missing", missing.isEmpty())
    }
}
