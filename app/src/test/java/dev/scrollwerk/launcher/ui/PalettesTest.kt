package dev.scrollwerk.launcher.ui

import dev.scrollwerk.launcher.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PalettesTest {
    @Test fun allNoctaliaBuiltInsArePresentAndUnique() {
        val names = Palettes.all.map { it.name }
        assertEquals(10, names.size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun lookupIsCaseInsensitiveSoOldConfigsStillResolve() {
        assertNotNull(Palettes.byName("noctalia")) // value written by the previous version
        assertNotNull(Palettes.byName("ROSÉ PINE"))
    }

    @Test fun lightAndDarkVariantsDiffer() {
        Palettes.all.forEach { assertNotEquals(it.name, it.dark.surface, it.light.surface) }
    }
}
