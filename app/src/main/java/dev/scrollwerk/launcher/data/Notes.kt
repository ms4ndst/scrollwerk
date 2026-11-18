package dev.scrollwerk.launcher.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** One line of the notes file as the widget shows it; [index] is its line number in the file. */
sealed interface NoteLine {
    val index: Int

    /** `- [ ] buy milk` / `- [x] done`. */
    data class Task(override val index: Int, val checked: Boolean, val text: String) : NoteLine
    /** `- item`. */
    data class Bullet(override val index: Int, val text: String) : NoteLine
    /** `# Heading`. */
    data class Heading(override val index: Int, val text: String) : NoteLine
    data class Text(override val index: Int, val text: String) : NoteLine
    data class Blank(override val index: Int) : NoteLine
}

/** The notes file is plain Markdown-ish text; only tasks and headings get special treatment. */
object Notes {
    const val FILE = "notes.md"
    private val task = Regex("""^(\s*[-*+]\s+\[)([ xX])(]\s?)(.*)$""")
    private val heading = Regex("""^#{1,6}\s+(.*)$""")
    private val bullet = Regex("""^\s*[-*+]\s+(.*)$""")
    /** Indentation, then any list or heading marker a line starts with. */
    private val marker = Regex("""^(\s*)([-*+] \[[ xX]] ?|[-*+] |#{1,6} )?""")

    fun parse(text: String): List<NoteLine> {
        val lines = text.lines()
        // A file that ends with a newline doesn't have an extra empty line.
        val count = if (lines.size > 1 && lines.last().isEmpty()) lines.size - 1 else lines.size
        return (0 until count).map { i ->
            val line = lines[i]
            task.matchEntire(line)?.let { m -> return@map NoteLine.Task(i, m.groupValues[2] != " ", m.groupValues[4]) }
            heading.matchEntire(line)?.let { m -> return@map NoteLine.Heading(i, m.groupValues[1].trim()) }
            bullet.matchEntire(line)?.let { m -> return@map NoteLine.Bullet(i, m.groupValues[1].trimEnd()) }
            if (line.isBlank()) NoteLine.Blank(i) else NoteLine.Text(i, line.trimEnd())
        }
    }

    /** What the editor's formatting buttons make a line. */
    enum class Kind(val prefix: String) { Task("- [ ] "), Bullet("- "), Heading("# ") }

    /** Text plus selection, as plain numbers so the editing rules are unit-testable. */
    data class Edit(val text: String, val start: Int, val end: Int = start)

    private fun kindOf(prefix: String): Kind? = when {
        prefix.isEmpty() -> null
        prefix.startsWith("#") -> Kind.Heading
        '[' in prefix -> Kind.Task
        else -> Kind.Bullet
    }

    /**
     * A formatting button: every line the selection touches becomes [kind], replacing any other
     * marker (a list item becomes a checkbox, …). If they all are [kind] already, they go back to
     * plain text. The selection moves with the text.
     */
    fun format(edit: Edit, kind: Kind): Edit {
        val text = edit.text
        val from = minOf(edit.start, edit.end).coerceIn(0, text.length)
        val to = maxOf(edit.start, edit.end).coerceIn(0, text.length)
        val firstLine = text.lastIndexOf('\n', from - 1) + 1
        val lastLineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
        val block = text.substring(firstLine, lastLineEnd).split('\n')
        val all = block.all { kindOf(marker.find(it)!!.groupValues[2]) == kind }
        var startShift = 0
        var endShift = 0
        var lineStart = firstLine
        val changed = block.map { line ->
            val m = marker.find(line)!!
            val indent = m.groupValues[1]
            val old = m.groupValues[2]
            val new = if (all) "" else kind.prefix
            val result = indent + new + line.substring(m.value.length)
            val delta = new.length - old.length
            val markerEnd = lineStart + indent.length + old.length
            // Positions after this line's marker move by delta; positions inside it snap to its end.
            if (from >= markerEnd) startShift += delta else if (from > lineStart + indent.length) startShift += (lineStart + indent.length + new.length) - from
            if (to >= markerEnd) endShift += delta else if (to > lineStart + indent.length) endShift += (lineStart + indent.length + new.length) - to
            lineStart += line.length + 1
            result
        }
        val out = text.substring(0, firstLine) + changed.joinToString("\n") + text.substring(lastLineEnd)
        val s = (from + startShift).coerceIn(0, out.length)
        val e = (to + endShift).coerceIn(0, out.length)
        return if (edit.start <= edit.end) Edit(out, s, e) else Edit(out, e, s)
    }

    /**
     * Enter in a list: when [new] is [old] with one line break typed at the cursor, the new line
     * starts with the same marker (a fresh, unticked checkbox). Enter on an empty item ends the
     * list instead. Anything else comes back unchanged.
     */
    fun continueList(old: String, new: Edit): Edit {
        val at = new.start
        if (new.start != new.end || new.text.length != old.length + 1 || at < 1 || new.text[at - 1] != '\n') return new
        if (new.text.removeRange(at - 1, at) != old) return new
        val lineStart = new.text.lastIndexOf('\n', at - 2) + 1
        val line = new.text.substring(lineStart, at - 1)
        val m = marker.find(line)!!
        val kind = kindOf(m.groupValues[2]) ?: return new
        if (kind == Kind.Heading) return new
        val indent = m.groupValues[1]
        if (line.substring(m.value.length).isBlank()) {
            // Empty item: drop its marker and the line break, leaving a plain empty line.
            val text = new.text.substring(0, lineStart) + new.text.substring(at)
            return Edit(text, lineStart)
        }
        val prefix = indent + kind.prefix
        return Edit(new.text.substring(0, at) + prefix + new.text.substring(at), at + prefix.length)
    }

    /** A line's marker as the editor draws it: [start]/[length] in the file text. */
    data class Marker(val start: Int, val length: Int, val kind: Kind, val checked: Boolean = false)

    /** Every line's list or heading marker, for drawing ☐ / ☑ / • and bold headings in the editor. */
    fun markers(text: String): List<Marker> {
        val out = ArrayList<Marker>()
        var lineStart = 0
        text.split('\n').forEach { line ->
            val m = marker.find(line)!!
            val prefix = m.groupValues[2]
            kindOf(prefix)?.let { kind ->
                val checked = kind == Kind.Task && prefix.contains(Regex("\\[[xX]]"))
                out += Marker(lineStart + m.groupValues[1].length, prefix.length, kind, checked)
            }
            lineStart += line.length + 1
        }
        return out
    }

    /**
     * Backspace into a marker (the editor shows it as one symbol) removes the whole marker, so a
     * checkbox doesn't turn into a broken "- [". Anything else comes back unchanged.
     */
    fun backspaceMarker(old: String, new: Edit): Edit {
        if (new.start != new.end || new.text.length != old.length - 1) return new
        val at = new.start
        if (old.removeRange(at, at + 1) != new.text) return new
        val hit = markers(old).firstOrNull { at >= it.start && at < it.start + it.length } ?: return new
        return Edit(old.removeRange(hit.start, hit.start + hit.length), hit.start)
    }

    /** [text] with the task on line [index] ticked or unticked; anything else is left as it is. */
    fun toggle(text: String, index: Int): String {
        val lines = text.split('\n').toMutableList()
        val line = lines.getOrNull(index) ?: return text
        val m = task.matchEntire(line.removeSuffix("\r")) ?: return text
        val mark = if (m.groupValues[2] == " ") "x" else " "
        lines[index] = m.groupValues[1] + mark + m.groupValues[3] + m.groupValues[4] + (if (line.endsWith("\r")) "\r" else "")
        return lines.joinToString("\n")
    }
}

/**
 * `notes.md` next to `config.json`, so it can be edited over USB or synced with Syncthing.
 * Edits are saved shortly after typing stops ([edit]) or right away ([save]); [reload] picks up
 * changes made outside the launcher unless an edit of ours is still waiting to be written.
 */
class NotesStore(private val file: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text
    private var pending: Job? = null

    init {
        scope.launch { _text.value = read() ?: "" }
    }

    /** Typing: saves half a second after the last change. */
    fun edit(text: String) {
        _text.value = text
        pending?.cancel()
        pending = scope.launch {
            delay(500)
            write(text)
        }
    }

    /** Ticking a task, leaving the editor, a restore: saves now. */
    fun save(text: String = _text.value) {
        _text.value = text
        pending?.cancel()
        pending = scope.launch { write(text) }
    }

    fun reload() {
        if (pending?.isActive == true) return
        scope.launch { read()?.let { if (it != _text.value) _text.value = it } }
    }

    /** The file's current text, for backups. */
    fun current(): String = _text.value

    private fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

    private suspend fun write(text: String) = writeLock.withLock {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(text)
            check(tmp.renameTo(file)) { "rename failed" }
        }
    }
}
