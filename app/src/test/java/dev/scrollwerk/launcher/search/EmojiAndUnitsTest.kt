package dev.scrollwerk.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiAndUnitsTest {
    private fun convert(q: String) = UnitConverter.evaluate(q)?.text

    @Test fun convertsEverydayUnits() {
        assertEquals("3.10686 mi", convert("5 km to mi"))
        assertEquals("22.2222 °C", convert("72 f in c"))
        assertEquals("473.176 ml", convert("2 cups to ml"))
        assertEquals("9.31323 GiB", convert("10 GB to gib"))
        assertEquals("62.1371 mph", convert("100 kmh to mph"))
        assertEquals("90 min", convert("1,5 h till min"))
        assertEquals("-40 °F", convert("-40 celsius as fahrenheit"))
        assertEquals("29.5735 ml", convert("1 fl oz to ml")) // two-word unit
    }

    @Test fun nonsenseIsNotAConversion() {
        assertNull(convert("5 km to kg"))    // different kinds
        assertNull(convert("5 km to km"))
        assertNull(convert("5 parsecs to m"))
        assertNull(convert("km to mi"))
        assertNull(convert("12*7"))
        assertNull(convert("open maps"))
    }

    @Test fun formatsSixSignificantDigits() {
        assertEquals("1609.34", UnitConverter.format(1609.344))
        assertEquals("0.000123457", UnitConverter.format(0.0001234567))
        assertEquals("100", UnitConverter.format(100.0))
    }

    private val sample = EmojiSearch.parse(
        "😀\tgrinning face\tface grin happy glad\n" +
            "🔥\tfire\tflame tool eld\n" +
            "❤️\tred heart\tlove heart hjärta kärlek\n" +
            "🇸🇪\tflag: Sweden\tsverige",
    )

    @Test fun emojiQueryNeedsTheColon() {
        assertEquals("fire", EmojiSearch.query(":fire"))
        assertEquals("", EmojiSearch.query(" :"))
        assertNull(EmojiSearch.query("fire"))
    }

    @Test fun emojiMatchesNamesThenKeywordsInEitherLanguage() {
        assertEquals("🔥", EmojiSearch.search(sample, "fire").first().item.emoji)
        assertEquals("❤️", EmojiSearch.search(sample, "hjärta").single().item.emoji)
        assertEquals("🇸🇪", EmojiSearch.search(sample, "sweden").first().item.emoji)
        assertEquals(4, EmojiSearch.search(sample, "").size)
    }

    @Test fun bundledEmojiFileParses() {
        val file = listOf(File("src/main/assets/emoji.tsv"), File("app/src/main/assets/emoji.tsv")).first { it.exists() }
        val all = EmojiSearch.parse(file.readText())
        assertTrue(all.size > 1500)
        assertTrue(all.all { it.name.isNotBlank() })
        assertEquals("😀", all.first().emoji)
        assertTrue(EmojiSearch.search(all, "thumbs up").any { it.item.emoji == "👍" })
    }
}
