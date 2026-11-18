package dev.scrollwerk.launcher.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything a backup holds, as file contents: the config, the workspaces, what the launcher learned
 * from use (launch counts, recents, pins), and the user's own palettes and fonts.
 * A backup is a plain zip of these files at the same paths, plus [MARKER], so it can be opened
 * and checked on a computer.
 */
data class BackupContents(
    val config: String? = null,
    val workspaces: String? = null,
    val usage: String? = null,
    /** File name → contents, for `palettes/` and `fonts/`. */
    val palettes: Map<String, ByteArray> = emptyMap(),
    val fonts: Map<String, ByteArray> = emptyMap(),
) {
    val isEmpty get() = config == null && workspaces == null && usage == null && palettes.isEmpty() && fonts.isEmpty()

    /** "config, workspaces, 2 palettes" — what a restore brings back. */
    fun summary(): String = buildList {
        if (config != null) add("config")
        if (workspaces != null) add("workspaces")
        if (usage != null) add("app usage")
        if (palettes.isNotEmpty()) add(count(palettes.size, "palette"))
        if (fonts.isNotEmpty()) add(count(fonts.size, "font"))
    }.joinToString(", ")

    private fun count(n: Int, what: String) = "$n $what${if (n == 1) "" else "s"}"

    // ByteArray fields: compare contents, not identity.
    override fun equals(other: Any?) = other is BackupContents && config == other.config && workspaces == other.workspaces &&
        usage == other.usage && sameFiles(palettes, other.palettes) && sameFiles(fonts, other.fonts)

    override fun hashCode() = listOf(config, workspaces, usage, palettes.keys, fonts.keys).hashCode()

    private fun sameFiles(a: Map<String, ByteArray>, b: Map<String, ByteArray>) =
        a.keys == b.keys && a.all { (k, v) -> v.contentEquals(b[k]) }
}

object Backup {
    /** Marks a zip as a Scrollwerk backup; holds the format version and when it was made. */
    const val MARKER = "scrollwerk-backup.json"
    const val CONFIG = "config.json"
    const val WORKSPACES = "workspaces.json"
    const val USAGE = "usage.json"
    private const val PALETTES = "palettes/"
    private const val FONTS = "fonts/"
    private const val VERSION = 1

    /** Per file and for the whole backup, so a wrong or hostile zip can't fill the phone. */
    const val MAX_FILE = 32L * 1024 * 1024
    const val MAX_TOTAL = 128L * 1024 * 1024

    class Invalid(message: String) : IOException(message)

    /** Suggested file name for a backup made on [date] ("2026-11-18"). */
    fun fileName(date: String) = "scrollwerk-backup-$date.zip"

    fun write(out: OutputStream, contents: BackupContents, createdAt: String) {
        ZipOutputStream(out).use { zip ->
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MARKER, """{ "format": $VERSION, "created": "$createdAt" }""".toByteArray())
            contents.config?.let { put(CONFIG, it.toByteArray()) }
            contents.workspaces?.let { put(WORKSPACES, it.toByteArray()) }
            contents.usage?.let { put(USAGE, it.toByteArray()) }
            contents.palettes.toSortedMap().forEach { (name, bytes) -> put(PALETTES + name, bytes) }
            contents.fonts.toSortedMap().forEach { (name, bytes) -> put(FONTS + name, bytes) }
        }
    }

    /**
     * Reads a backup. Throws [Invalid] for anything that isn't one: no [MARKER], or a file that
     * isn't one of ours. Only known paths are read, so nothing can land outside our folders.
     */
    fun read(input: InputStream): BackupContents {
        var marked = false
        var total = 0L
        var config: String? = null
        var workspaces: String? = null
        var usage: String? = null
        val palettes = LinkedHashMap<String, ByteArray>()
        val fonts = LinkedHashMap<String, ByteArray>()
        val zip = try {
            ZipInputStream(input)
        } catch (e: Exception) {
            throw Invalid("Not a zip file")
        }
        zip.use {
            while (true) {
                val entry = try {
                    zip.nextEntry
                } catch (e: Exception) {
                    throw Invalid("Not a zip file, or a damaged one")
                } ?: break
                if (entry.isDirectory) continue
                val bytes = readLimited(zip, entry.name)
                total += bytes.size
                if (total > MAX_TOTAL) throw Invalid("The backup is too large")
                val path = entry.name
                when {
                    path == MARKER -> marked = true
                    path == CONFIG -> config = bytes.decodeToString()
                    path == WORKSPACES -> workspaces = bytes.decodeToString()
                    path == USAGE -> usage = bytes.decodeToString()
                    path.startsWith(PALETTES) -> palettes[safeName(path, PALETTES)] = bytes
                    path.startsWith(FONTS) -> fonts[safeName(path, FONTS)] = bytes
                    else -> throw Invalid("Unexpected file in the backup: $path")
                }
            }
        }
        if (!marked) throw Invalid("Not a Scrollwerk backup")
        return BackupContents(config, workspaces, usage, palettes, fonts)
    }

    /** The plain file name after [prefix]; refuses sub-folders, "..", hidden and empty names. */
    private fun safeName(path: String, prefix: String): String {
        val name = path.removePrefix(prefix)
        if (name.isEmpty() || name.startsWith(".") || '/' in name || '\\' in name || name.length > 120) {
            throw Invalid("Unexpected file in the backup: $path")
        }
        return name
    }

    private fun readLimited(zip: ZipInputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_FILE) throw Invalid("$name is too large")
        }
        return out.toByteArray()
    }
}

/** Where the Control Center's backup section stands. */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Working(val text: String) : BackupStatus
    /** A backup was picked and read; waiting for "Restore" before anything is replaced. */
    data class Confirm(val fileName: String, val contents: BackupContents) : BackupStatus
    data class Done(val text: String) : BackupStatus
    data class Failed(val text: String) : BackupStatus
}
