package dev.scrollwerk.launcher.ui

import androidx.compose.ui.graphics.Color
import dev.scrollwerk.launcher.ui.theme.PaletteImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteImportTest {
    private fun ok(file: String, text: String) =
        (PaletteImport.parse(file, text) as? PaletteImport.Result.Ok)?.palette ?: error("not parsed: ${PaletteImport.parse(file, text)}")

    @Test fun noctaliaSchemeWithBothVariants() {
        val p = ok("ignored.json", """
            {
              "name": "Everforest",
              "dark":  { "mPrimary": "#A7C080", "mOnPrimary": "#2D353B", "mSurface": "#2D353B", "mOnSurface": "#D3C6AA",
                         "mSurfaceVariant": "#343F44", "mOutline": "#475258", "mError": "#E67E80" },
              "light": { "mPrimary": "#8DA101", "mSurface": "#FDF6E3", "mOnSurface": "#5C6A72" },
            }
        """)
        assertEquals("Everforest", p.name)
        assertEquals(Color(0xFFA7C080), p.dark.primary)
        assertEquals(Color(0xFF343F44), p.dark.surfaceContainer) // mSurfaceVariant
        assertEquals(Color(0xFF475258), p.dark.outlineVariant)   // mOutline
        assertEquals(Color(0xFFFDF6E3), p.light.surface)
        assertEquals(p.light.primary, p.light.secondary)          // missing roles are derived
    }

    @Test fun importedPalettesCanNameFonts() {
        val p = ok("x.json", """{ "name": "Ink", "font": "Inter", "headingFont": "Gugi", "primary": "#ff8800", "surface": "#101010" }""")
        assertEquals("Inter", p.font)
        assertEquals("Gugi", p.headingFont)
        val pair = dev.scrollwerk.launcher.ui.theme.ThemeFonts.forPalette(p)
        assertEquals("Inter", pair.text?.label)
        assertEquals("Gugi", pair.heading?.label)
        assertEquals(null, ok("y.json", """{ "primary": "#ff8800", "surface": "#101010" }""").font)
    }

    @Test fun plainKeysAndSingleVariantFiles() {
        val p = ok("my_theme.json", """{ "primary": "#ff8800", "surface": "#101010", "on_surface": "#eeeeee" }""")
        assertEquals("my theme", p.name)
        assertEquals(p.dark, p.light)
        assertEquals(Color(0xFFEEEEEE), p.dark.onSurface)
    }

    private val gruvboxYaml = """
        system: "base16"
        name: "Gruvbox dark, medium"
        variant: "dark"
        palette:
          base00: "#282828"
          base01: "#3c3836"
          base02: "#504945"
          base03: "#665c54"
          base04: "#bdae93"
          base05: "#d5c4a1"
          base06: "#ebdbb2"
          base07: "#fbf1c7"
          base08: "#fb4934"
          base09: "#fe8019"
          base0A: "#fabd2f"
          base0B: "#b8bb26"
          base0C: "#8ec07c"
          base0D: "#83a598"
          base0E: "#d3869b"
          base0F: "#d65d0e"
    """.trimIndent()

    @Test fun base16YamlMapsOntoNoctaliaRolesAndDerivesTheOtherMode() {
        val p = ok("gruvbox-dark-medium.yaml", gruvboxYaml)
        assertEquals("Gruvbox dark, medium", p.name)
        assertEquals(Color(0xFF282828), p.dark.surface)
        assertEquals(Color(0xFF83A598), p.dark.primary)
        assertEquals(Color(0xFFFB4934), p.dark.error)
        assertEquals(Color(0xFFFBF1C7), p.light.surface)   // flipped ramp
        assertEquals(Color(0xFF504945), p.light.onSurface)
    }

    @Test fun oldBase16FormatWithoutHashesAndLightVariantDetection() {
        val text = (0..15).joinToString("\n", prefix = "scheme: \"Paper\"\n") { i ->
            val v = if (i < 8) listOf("ffffff", "f2f2f2", "e0e0e0", "c0c0c0", "808080", "303030", "202020", "101010")[i] else "aa3355"
            "base0%X: \"%s\"".format(i, v)
        }
        val p = ok("paper.yaml", text)
        assertEquals("Paper", p.name)
        assertEquals(Color(0xFFFFFFFF), p.light.surface) // light scheme keeps its own ramp in light mode
        assertEquals(Color(0xFF101010), p.dark.surface)
    }

    @Test fun badFilesSayWhy() {
        val missing = PaletteImport.parse("x.yaml", "base00: \"000000\"\nbase01: \"111111\"")
        assertTrue(missing is PaletteImport.Result.Error && "base02" in missing.message)
        assertTrue(PaletteImport.parse("x.json", """{ "hello": 1 }""") is PaletteImport.Result.Error)
        assertTrue(PaletteImport.parse("x.json", "not json") is PaletteImport.Result.Error)
    }
}
