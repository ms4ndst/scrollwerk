package dev.scrollwerk.launcher.search

/** One emoji from assets/emoji.tsv: the emoji, its English name, and English + Swedish keywords. */
data class EmojiEntry(val emoji: String, val name: String, val keywords: String)

/** rofimoji in the launcher: a query starting with ':' searches emoji by name and keyword. */
object EmojiSearch {
    const val PREFIX = ':'
    const val LIMIT = 40
    private const val KEYWORD_SCORE = 5

    /** The emoji query if [raw] is one (":fire" → "fire", ":" → ""), else null. */
    fun query(raw: String): String? = raw.trimStart().takeIf { it.firstOrNull() == PREFIX }?.drop(1)?.trim()

    fun parse(tsv: String): List<EmojiEntry> = tsv.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2 || parts[0].isEmpty()) null else EmojiEntry(parts[0], parts[1], parts.getOrElse(2) { "" })
    }.toList()

    /** Name matches first (fuzzy, highlighted), then keyword matches; an empty query lists the first ones. */
    fun search(all: List<EmojiEntry>, query: String, limit: Int = LIMIT): List<Scored<EmojiEntry>> {
        val q = query.trim()
        if (q.isEmpty()) return all.take(limit).map { Scored(it, IntArray(0), 0) }
        val words = q.lowercase().split(' ').filter { it.isNotEmpty() }
        return all.withIndex().mapNotNull { (i, e) ->
            FuzzyMatcher.match(q, e.name)?.let { return@mapNotNull Scored(e, it.positions, it.score) to i }
            val keys = e.keywords.split(' ')
            if (words.all { w -> keys.any { it.startsWith(w) } }) Scored(e, IntArray(0), KEYWORD_SCORE) to i else null
        }
            .sortedWith(compareByDescending<Pair<Scored<EmojiEntry>, Int>> { it.first.score }.thenBy { it.second })
            .take(limit)
            .map { it.first }
    }
}
