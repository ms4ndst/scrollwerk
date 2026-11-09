package dev.scrollwerk.launcher.search

import dev.scrollwerk.launcher.data.AppEntry
import dev.scrollwerk.launcher.data.UsageState
import kotlin.math.ln

sealed interface AppFilter {
    data object All : AppFilter
    data object Recent : AppFilter
    data class Category(val name: String) : AppFilter
}

/** One result row. [matched] are indices into the label to highlight. */
data class AppResult(val app: AppEntry, val matched: IntArray, val pinned: Boolean) {
    override fun equals(other: Any?) =
        other is AppResult && app == other.app && pinned == other.pinned && matched.contentEquals(other.matched)

    override fun hashCode() = 31 * app.hashCode() + matched.contentHashCode()
}

/**
 * Ranking for the launcher, mirroring Noctalia's application provider:
 * - empty query: pinned apps first (in pin order), then the rest A–Z;
 * - Recent filter with empty query: most recently launched first;
 * - otherwise: fuzzy score on the label (falling back to a package-name substring),
 *   plus a usage boost, best first.
 */
object AppSearch {

    fun search(
        apps: List<AppEntry>,
        query: String,
        usage: UsageState,
        filter: AppFilter = AppFilter.All,
        sortByUsage: Boolean = true,
    ): List<AppResult> {
        val pinned = usage.pinned.toHashSet()
        val pool = when (filter) {
            AppFilter.All -> apps
            AppFilter.Recent -> apps.associateBy { it.id }.let { byId -> usage.recent.mapNotNull(byId::get) }
            is AppFilter.Category -> apps.filter { it.category == filter.name }
        }
        val byLabel = compareBy(String.CASE_INSENSITIVE_ORDER, AppEntry::label).thenBy(AppEntry::id)
        val q = query.trim()

        if (q.isEmpty()) {
            if (filter == AppFilter.Recent) return pool.map { AppResult(it, NO_MATCH, it.id in pinned) }
            val (pinnedApps, rest) = pool.partition { it.id in pinned }
            val pinOrder = usage.pinned.withIndex().associate { (i, id) -> id to i }
            // Browsing stays a stable A–Z list; usage only ranks search results (and has its own Recent chip).
            val restSorted = rest.sortedWith(byLabel)
            return pinnedApps.sortedBy { pinOrder[it.id] }.map { AppResult(it, NO_MATCH, true) } +
                restSorted.map { AppResult(it, NO_MATCH, false) }
        }

        class Scored(val app: AppEntry, val score: Int, val matched: IntArray)
        return pool.mapNotNull { app ->
            val label = FuzzyMatcher.match(q, app.label)
            val base: Scored = when {
                label != null -> Scored(app, label.score, label.positions)
                q.length >= 3 && app.packageName.contains(q, ignoreCase = true) -> Scored(app, PACKAGE_SCORE, NO_MATCH)
                else -> return@mapNotNull null
            }
            val boost = if (sortByUsage) usageBoost(usage.count(app.id)) else 0
            Scored(app, base.score + boost + (if (app.id in pinned) PIN_BOOST else 0), base.matched)
        }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label })
            .map { AppResult(it.app, it.matched, it.app.id in pinned) }
    }

    /** Categories present in [apps], for the chip row. */
    fun categories(apps: List<AppEntry>): List<String> = apps.mapNotNull { it.category }.distinct().sorted()

    /** Logarithmic: the 1st launches matter, the 500th barely does. */
    internal fun usageBoost(count: Int): Int = (ln(1.0 + count) * 6).toInt()

    private const val PACKAGE_SCORE = 12
    private const val PIN_BOOST = 4
    private val NO_MATCH = IntArray(0)
}
