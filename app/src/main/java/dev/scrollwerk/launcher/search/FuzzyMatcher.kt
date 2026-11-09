package dev.scrollwerk.launcher.search

import kotlin.math.min

/**
 * fzf-style fuzzy matcher: the query must appear in the text as a subsequence
 * (case-insensitive, query whitespace ignored), and among all ways it can appear, the
 * best-scoring alignment wins. Scoring favours matches at word starts and camelCase humps,
 * consecutive runs, and early positions; gaps cost a little. O(query x text).
 */
object FuzzyMatcher {

    class Match(val score: Int, val positions: IntArray)

    private const val MATCH = 16
    private const val BOUNDARY = 10
    private const val CAMEL = 8
    private const val FIRST_CHAR_MULTIPLIER = 2
    private const val CONSECUTIVE = 6
    private const val MAX_LEADING_PENALTY = 12
    private const val NONE = Int.MIN_VALUE / 4

    fun match(query: String, text: String): Match? {
        val q = query.filterNot(Char::isWhitespace).lowercase()
        if (q.isEmpty()) return Match(0, IntArray(0))
        val lower = text.lowercase()
        val n = q.length
        val m = lower.length
        if (n > m || !isSubsequence(q, lower)) return null

        val bonus = IntArray(m) { boundaryBonus(text, it) }
        val score = Array(n) { IntArray(m) { NONE } }
        val from = Array(n) { IntArray(m) { -1 } }

        for (j in 0 until m) {
            if (lower[j] == q[0]) score[0][j] = MATCH + bonus[j] * FIRST_CHAR_MULTIPLIER - min(j, MAX_LEADING_PENALTY)
        }
        for (i in 1 until n) {
            // Best predecessor k <= j-2 under a gap penalty of (j - k + 1): track max(score + k).
            var bestGapped = NONE
            var bestGappedAt = -1
            for (j in 0 until m) {
                if (j >= 2) {
                    val prev = score[i - 1][j - 2]
                    if (prev > NONE && prev + (j - 2) > bestGapped) {
                        bestGapped = prev + (j - 2)
                        bestGappedAt = j - 2
                    }
                }
                if (lower[j] != q[i]) continue
                var best = NONE
                var at = -1
                if (j >= 1 && score[i - 1][j - 1] > NONE) {
                    best = score[i - 1][j - 1] + CONSECUTIVE
                    at = j - 1
                }
                if (bestGappedAt >= 0 && bestGapped - j - 1 > best) {
                    best = bestGapped - j - 1
                    at = bestGappedAt
                }
                if (at < 0) continue
                score[i][j] = best + MATCH + bonus[j]
                from[i][j] = at
            }
        }

        var end = -1
        for (j in 0 until m) if (score[n - 1][j] > NONE && (end < 0 || score[n - 1][j] > score[n - 1][end])) end = j
        if (end < 0) return null

        val positions = IntArray(n)
        var j = end
        for (i in n - 1 downTo 0) {
            positions[i] = j
            j = from[i][j]
        }
        // Small length penalty so "Maps" beats "Maps Go Lite Edition" on equal matches.
        return Match(score[n - 1][end] - m / 8, positions)
    }

    private fun isSubsequence(q: String, text: String): Boolean {
        var k = 0
        for (c in text) if (k < q.length && c == q[k]) k++
        return k == q.length
    }

    private fun boundaryBonus(text: String, j: Int): Int {
        if (j == 0) return BOUNDARY
        val prev = text[j - 1]
        val cur = text[j]
        return when {
            !prev.isLetterOrDigit() -> BOUNDARY
            prev.isLowerCase() && cur.isUpperCase() -> CAMEL
            prev.isLetter() && cur.isDigit() -> CAMEL
            else -> 0
        }
    }
}
