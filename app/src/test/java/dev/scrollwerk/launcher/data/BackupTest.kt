package dev.scrollwerk.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    private val full = BackupContents(
        config = "{ /* mine */ \"theme\": { \"palette\": \"Nord\" } }",
        workspaces = "{\"workspaces\":[]}",
        usage = "{\"counts\":{\"a\":3}}",
        notes = "# Today\n- [ ] milk\n",
        palettes = mapOf("mine.json" to byteArrayOf(1, 2, 3)),
        fonts = mapOf("Cool.ttf" to ByteArray(1000) { it.toByte() }, "Other.otf" to byteArrayOf(9)),
    )

    private fun bytes(contents: BackupContents) =
        ByteArrayOutputStream().also { Backup.write(it, contents, "2026-11-18T10:00:00") }.toByteArray()

    private fun zip(vararg files: Pair<String, String>) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (name, text) -> z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun roundTripKeepsEverything() {
        assertEquals(full, Backup.read(ByteArrayInputStream(bytes(full))))
    }

    @Test fun partialBackupsRestoreWhatTheyHave() {
        val onlyConfig = BackupContents(config = "{}")
        assertEquals(onlyConfig, Backup.read(ByteArrayInputStream(bytes(onlyConfig))))
    }

    @Test fun summaryNamesWhatIsInside() {
        assertEquals("config, workspaces, app usage, notes, 1 palette, 2 fonts", full.summary())
    }

    @Test fun refusesZipsThatAreNotBackups() {
        val e = assertThrows(Backup.Invalid::class.java) { Backup.read(ByteArrayInputStream(zip("config.json" to "{}"))) }
        assertEquals("Not a Scrollwerk backup", e.message)
    }

    @Test fun refusesPathsOutsideItsFolders() {
        listOf("../evil.json", "palettes/../../evil", "fonts/sub/x.ttf", "palettes/.hidden", "shared_prefs/x.xml").forEach { path ->
            assertThrows(path, Backup.Invalid::class.java) {
                Backup.read(ByteArrayInputStream(zip(Backup.MARKER to "{}", path to "x")))
            }
        }
    }

    @Test fun refusesFilesThatAreNotZips() {
        assertThrows(Backup.Invalid::class.java) { Backup.read(ByteArrayInputStream("not a zip at all".toByteArray())) }
    }
}
