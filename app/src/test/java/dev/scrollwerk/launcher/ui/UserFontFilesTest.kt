package dev.scrollwerk.launcher.ui

import dev.scrollwerk.launcher.ui.theme.UserFontFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFontFilesTest {
    @Test fun filesGroupIntoFamiliesByWeight() {
        val g = UserFontFiles.group(listOf(
            "JapanRamen-Regular.otf", "JapanRamen-Bold.otf", "JapanRamen-SemiBold.otf", "JapanRamen-BoldItalic.otf",
            "The Last Shuriken.ttf", "notes.txt",
        ))
        assertEquals(listOf("JapanRamen", "The Last Shuriken"), g.keys.toList())
        assertEquals(mapOf(400 to "JapanRamen-Regular.otf", 600 to "JapanRamen-SemiBold.otf", 700 to "JapanRamen-Bold.otf"), g["JapanRamen"])
        assertEquals(mapOf(400 to "The Last Shuriken.ttf"), g["The Last Shuriken"])
    }

    @Test fun separatorsAndCaseDontMatter() {
        val g = UserFontFiles.group(listOf("my_font_medium.TTF", "my_font.ttf", "Bold.otf"))
        assertEquals(mapOf(400 to "my_font.ttf", 500 to "my_font_medium.TTF"), g["my font"])
        assertEquals(mapOf(700 to "Bold.otf"), g["Bold"]) // a lone weight word is the name
    }
}
