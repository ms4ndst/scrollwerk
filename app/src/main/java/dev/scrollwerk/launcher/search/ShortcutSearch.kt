package dev.scrollwerk.launcher.search

/** An app shortcut (long-press action), e.g. Gmail's "Compose". [appId] is the app it belongs to. */
data class ShortcutEntry(val id: String, val appId: String, val packageName: String, val label: String)

/** One shortcut result row. [matched] are indices into the shortcut's label to highlight. */
data class ShortcutResult(val shortcut: ShortcutEntry, val appLabel: String, val matched: IntArray) {
    override fun equals(other: Any?) =
        other is ShortcutResult && shortcut == other.shortcut && appLabel == other.appLabel && matched.contentEquals(other.matched)

    override fun hashCode() = 31 * shortcut.hashCode() + matched.contentHashCode()
}

/**
 * Shortcut results for the launcher, shown after the apps: fuzzy on the shortcut's own label
 * ("comp" → Compose), or on its app's label ("gmail" → Gmail's shortcuts), the former first.
 */
object ShortcutSearch {
    const val MIN_QUERY = 2
    const val LIMIT = 6
    private const val APP_MATCH_PENALTY = 20

    fun search(shortcuts: List<ShortcutEntry>, appLabels: Map<String, String>, query: String, limit: Int = LIMIT): List<ShortcutResult> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        class Scored(val result: ShortcutResult, val score: Int)
        return shortcuts.mapNotNull { s ->
            val appLabel = appLabels[s.appId] ?: return@mapNotNull null // app hidden or gone
            FuzzyMatcher.match(q, s.label)?.let { return@mapNotNull Scored(ShortcutResult(s, appLabel, it.positions), it.score) }
            FuzzyMatcher.match(q, appLabel)?.let { Scored(ShortcutResult(s, appLabel, IntArray(0)), it.score - APP_MATCH_PENALTY) }
        }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.result.appLabel }.thenBy { it.result.shortcut.label })
            .take(limit)
            .map { it.result }
    }
}
