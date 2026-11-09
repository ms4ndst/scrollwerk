package dev.scrollwerk.launcher.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private fun score(q: String, t: String) = FuzzyMatcher.match(q, t)!!.score

    @Test fun nonSubsequenceDoesNotMatch() {
        assertNull(FuzzyMatcher.match("xyz", "Firefox"))
        assertNull(FuzzyMatcher.match("firefoxes", "Firefox"))
    }

    @Test fun caseInsensitiveAndIgnoresQuerySpaces() {
        assertNotNull(FuzzyMatcher.match("FIRE", "firefox"))
        assertArrayEquals(intArrayOf(0, 7), FuzzyMatcher.match("g m", "Google Maps")!!.positions)
    }

    @Test fun emptyQueryMatchesEverything() {
        assertEquals(0, FuzzyMatcher.match("  ", "Anything")!!.positions.size)
    }

    @Test fun prefersWordStartOverEarlierMidWordHit() {
        // "a" appears early in "Calendar", but the word start of "App" scores higher.
        assertArrayEquals(intArrayOf(9), FuzzyMatcher.match("a", "Calendar App")!!.positions)
    }

    @Test fun prefixBeatsMidWordSubstring() {
        assertTrue(score("cal", "Calendar") > score("cal", "Local news"))
    }

    @Test fun consecutiveBeatsScattered() {
        assertTrue(score("set", "Settings") > score("set", "Smart Expense Tracker"))
    }

    @Test fun camelCaseHumpCountsAsBoundary() {
        assertArrayEquals(intArrayOf(0, 7), FuzzyMatcher.match("ym", "YouTubeMusic")!!.positions)
    }

    @Test fun shorterLabelWinsOnEqualMatch() {
        assertTrue(score("maps", "Maps") > score("maps", "Maps Go Lite Edition"))
    }
}
