package dev.scrollwerk.launcher.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.scrollwerk.launcher.R

/**
 * Touches that make a theme feel like where it comes from, beyond colours and font:
 * [kanji] writes the workspace numbers in brush kanji (一, 二, 三…).
 */
data class ThemeExtras(val kanji: Boolean = false) {
    companion object {
        val NONE = ThemeExtras()

        fun forPalette(name: String?): ThemeExtras = when {
            name.equals("Kanagawa", ignoreCase = true) -> ThemeExtras(kanji = true)
            else -> NONE
        }
    }
}

/** Yuji Syuku's brush kanji, subset to the numerals [Japanese] writes (tools/gen_fonts.py). */
val KanjiBrush = FontFamily(Font(R.font.kanji_brush_regular))

object Japanese {
    private const val DIGITS = "〇一二三四五六七八九"

    /** 1 → 一, 10 → 十, 12 → 十二, 20 → 二十, 99 → 九十九; outside 0–99, plain digits. */
    fun numeral(n: Int): String = when {
        n !in 0..99 -> n.toString()
        n < 10 -> DIGITS[n].toString()
        else -> {
            val tens = n / 10
            val ones = n % 10
            (if (tens > 1) DIGITS[tens].toString() else "") + "十" + (if (ones > 0) DIGITS[ones].toString() else "")
        }
    }
}
