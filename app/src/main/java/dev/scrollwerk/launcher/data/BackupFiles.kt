package dev.scrollwerk.launcher.data

import dev.scrollwerk.launcher.config.ConfigStore
import dev.scrollwerk.launcher.config.LauncherConfig
import java.io.File

/**
 * Gathers the launcher's files into a [BackupContents] and puts one back. Call off the main thread.
 * [acceptsPalette] / [acceptsFont] are the folders' own file filters, so a backup can't add files
 * those folders would ignore.
 */
class BackupFiles(
    private val config: ConfigStore,
    private val workspaces: WorkspaceStore,
    private val usage: UsageStore,
    private val notes: NotesStore,
    private val paletteDir: File,
    private val fontDir: File,
    private val acceptsPalette: (String) -> Boolean,
    private val acceptsFont: (String) -> Boolean,
) {
    fun collect() = BackupContents(
        config = config.readText(),
        workspaces = workspaces.export(),
        usage = usage.export(),
        notes = notes.current().takeIf { it.isNotEmpty() },
        palettes = folder(paletteDir, acceptsPalette),
        fonts = folder(fontDir, acceptsFont),
    )

    /**
     * Checks everything first, then replaces the config, workspaces and usage, and adds the palettes
     * and fonts (ones with the same name are replaced; your others stay). Nothing is written if a
     * file doesn't parse. Returns what was restored.
     */
    fun restore(contents: BackupContents): String {
        if (contents.isEmpty) throw Backup.Invalid("The backup is empty")
        contents.config?.let { text ->
            LauncherConfig.parse(text).exceptionOrNull()?.let {
                throw Backup.Invalid("The backup's config.json has an error: ${it.message?.lineSequence()?.firstOrNull()}")
            }
        }
        val ws = contents.workspaces?.let { runCatching { workspaces.parse(it) }.getOrElse { throw Backup.Invalid("The backup's workspaces.json is damaged") } }
        val use = contents.usage?.let { runCatching { usage.parse(it) }.getOrElse { throw Backup.Invalid("The backup's usage.json is damaged") } }
        val palettes = contents.palettes.filterKeys(acceptsPalette)
        val fonts = contents.fonts.filterKeys(acceptsFont)

        contents.config?.let { if (!config.writeText(it)) throw Backup.Invalid("Couldn't write config.json") }
        ws?.let { state -> workspaces.update { state } }
        use?.let { state -> usage.update { state } }
        contents.notes?.let(notes::save)
        write(paletteDir, palettes)
        write(fontDir, fonts)
        return contents.copy(palettes = palettes, fonts = fonts).summary()
    }

    private fun folder(dir: File, accepts: (String) -> Boolean): Map<String, ByteArray> =
        dir.listFiles { f -> f.isFile && accepts(f.name) }.orEmpty()
            .sortedBy { it.name }
            .associate { it.name to it.readBytes() }

    private fun write(dir: File, files: Map<String, ByteArray>) {
        if (files.isEmpty()) return
        dir.mkdirs()
        files.forEach { (name, bytes) ->
            val tmp = File(dir, ".$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(File(dir, name))) {
                tmp.delete()
                throw Backup.Invalid("Couldn't write ${dir.name}/$name")
            }
        }
    }
}
