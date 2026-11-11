package dev.scrollwerk.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconGlyphTest {
    private val blue = 0xFF1E4FD8.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val clear = 0x00000000

    @Test fun glyphOnABackgroundKeepsOnlyTheGlyph() {
        // 8 background pixels, 2 glyph pixels, 2 transparent corners.
        val px = IntArray(8) { blue } + IntArray(2) { white } + IntArray(2) { clear }
        val mask = IconGlyph.alphaMask(px)
        assertTrue(mask.take(8).all { it == 0 })
        assertTrue(mask.slice(8..9).all { it == 255 })
        assertTrue(mask.takeLast(2).all { it == 0 })
    }

    @Test fun darkGlyphOnLightBackgroundWorksToo() {
        val px = IntArray(8) { white } + IntArray(2) { blue }
        val mask = IconGlyph.alphaMask(px)
        assertTrue(mask.take(8).all { it == 0 })
        assertTrue(mask.takeLast(2).all { it > 200 })
    }

    @Test fun singleColourLogoBecomesItsSilhouette() {
        val px = IntArray(5) { blue } + IntArray(3) { clear } + intArrayOf(0x801E4FD8.toInt())
        assertEquals(listOf(255, 255, 255, 255, 255, 0, 0, 0, 128), IconGlyph.alphaMask(px).toList())
    }

    @Test fun fullyTransparentIconStaysEmpty() {
        assertTrue(IconGlyph.alphaMask(IntArray(4) { clear }).all { it == 0 })
    }
}
