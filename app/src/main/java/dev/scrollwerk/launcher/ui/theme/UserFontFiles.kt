package dev.scrollwerk.launcher.ui.theme

/**
 * Groups font files from the `fonts/` folder into families by name: "JapanRamen-Regular.otf" and
 * "JapanRamen-Bold.otf" become "JapanRamen" with weights 400 and 700. A file without a weight in
 * its name is that family's regular.
 */
object UserFontFiles {
    private val EXTENSIONS = setOf("ttf", "otf")

    /** Weight words, longest first so "SemiBold" isn't read as "Bold". */
    private val WEIGHTS = listOf(
        "extrabold" to 800, "semibold" to 600, "demibold" to 600, "extralight" to 200, "ultralight" to 200,
        "regular" to 400, "medium" to 500, "normal" to 400, "light" to 300, "black" to 900, "heavy" to 900,
        "bold" to 700, "thin" to 100, "book" to 400,
    )

    fun accepts(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Family label → (weight → file name), A–Z. Italic files are skipped; the UI has no italics. */
    fun group(fileNames: List<String>): Map<String, Map<Int, String>> {
        val families = sortedMapOf<String, MutableMap<Int, String>>(String.CASE_INSENSITIVE_ORDER)
        fileNames.filter(::accepts).sorted().forEach { file ->
            val stem = file.substringBeforeLast('.')
            if (Regex("italic|oblique", RegexOption.IGNORE_CASE).containsMatchIn(stem)) return@forEach
            val parts = stem.split('-', '_', ' ').filter { it.isNotEmpty() }
            val last = parts.lastOrNull()?.lowercase().orEmpty()
            val weight = WEIGHTS.firstOrNull { last == it.first }?.second
            val name = (if (weight != null && parts.size > 1) parts.dropLast(1) else parts).joinToString(" ").ifEmpty { stem }
            families.getOrPut(name) { mutableMapOf() }.putIfAbsent(weight ?: 400, file)
        }
        return families
    }

    /** Every usable family in [dir] and one message per file that didn't load. Files are checked by opening them. */
    fun loadFolder(dir: java.io.File): Pair<List<FontOption>, List<String>> {
        val names = dir.listFiles { f -> f.isFile && accepts(f.name) }.orEmpty().map { it.name }
        val errors = mutableListOf<String>()
        val options = group(names).mapNotNull { (label, weights) ->
            val ok = weights.filterValues { name ->
                val file = java.io.File(dir, name)
                (runCatching { android.graphics.Typeface.Builder(file).build() }.getOrNull() != null)
                    .also { if (!it) errors += "fonts/$name: not a font Android can read" }
            }
            if (ok.isEmpty()) null else FontOption(label, ok.mapValues { FontRef.FileRef(java.io.File(dir, it.value)) })
        }
        return options to errors
    }
}
