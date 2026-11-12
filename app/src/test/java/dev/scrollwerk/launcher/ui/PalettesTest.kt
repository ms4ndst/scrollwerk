package dev.scrollwerk.launcher.ui

import dev.scrollwerk.launcher.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import dev.scrollwerk.launcher.ui.theme.ThemeFont
import dev.scrollwerk.launcher.ui.theme.ThemeFonts
import dev.scrollwerk.launcher.ui.theme.FontPair
import org.junit.Test

class PalettesTest {
    @Test fun allBuiltInsArePresentUniqueAndSorted() {
        val names = Palettes.all.map { it.name }
        assertEquals(16, names.size)
        assertEquals(names.size, names.toSet().size)
        assertEquals(names.sortedBy { it.lowercase() }, names)
        listOf("Everforest", "Solarized", "One", "Monokai Pro", "Synthwave '84", "Oxocarbon").forEach { assertNotNull(it, Palettes.byName(it)) }
    }

    @Test fun everyThemeButNoctaliaHasAFont() {
        Palettes.builtIn.forEach { p ->
            val pair = ThemeFonts.forPalette(p)
            if (p.name == "Noctalia") assertNull(pair.text) else assertNotNull(p.name, pair.text)
        }
        val kanagawa = ThemeFonts.forPalette(Palettes.byName("kanagawa"))
        assertEquals("Zen Kaku Gothic New", kanagawa.text?.label)
        assertEquals("ZCOOL QingKe HuangYou", kanagawa.heading?.label)
        assertEquals("Pixelify Sans", ThemeFonts.forPalette(Palettes.byName("Tokyo-Night")).text?.label)
        assertNull(ThemeFonts.forPalette(Palettes.byName("Nord")).heading)
    }

    @Test fun fontSettingsResolve() {
        val kanagawa = Palettes.byName("Kanagawa")
        assertEquals(FontPair(ThemeFont.ZenKakuGothicNew.option, ThemeFont.ZcoolQingKeHuangYou.option), ThemeFonts.resolve("theme", "theme", kanagawa))
        assertEquals(FontPair(null, ThemeFont.ZcoolQingKeHuangYou.option), ThemeFonts.resolve("system", "theme", kanagawa))
        assertEquals(FontPair(ThemeFont.Inter.option, null), ThemeFonts.resolve("inter", "text", kanagawa))
        assertEquals(ThemeFont.Lora.option, ThemeFonts.resolve("theme", "Lora", kanagawa).heading)
        assertEquals(ThemeFont.ZenKakuGothicNew.option, ThemeFonts.resolve("Comic Sans", "theme", kanagawa).text) // unknown → the theme's
        assertEquals(FontPair(null, null), ThemeFonts.resolve("theme", "theme", null)) // wallpaper colours: the phone's font
        assertNull(ThemeFonts.family(FontPair(null, null)))
    }

    @Test fun headingFontsStandInForSemiBoldAndBoldOnly() {
        val fonts = ThemeFonts.family(FontPair(ThemeFont.ZenKakuGothicNew.option, ThemeFont.ZcoolQingKeHuangYou.option))!!
        val weights = (fonts as androidx.compose.ui.text.font.FontListFontFamily).fonts.map { it.weight.weight }
        assertEquals(listOf(400, 500, 600, 700), weights.sorted()) // Zen Kaku's own bold is left out
    }

    @Test fun lightAndDarkVariantsDiffer() {
        Palettes.all.forEach { assertNotEquals(it.name, it.dark.surface, it.light.surface) }
    }
}
