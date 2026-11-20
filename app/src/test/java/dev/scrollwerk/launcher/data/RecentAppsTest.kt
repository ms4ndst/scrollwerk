package dev.scrollwerk.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    private fun app(pkg: String, activity: String = "Main") = AppEntry("$pkg/.$activity", pkg.substringAfterLast('.'), pkg)
    private val apps = listOf(app("com.vivaldi.browser"), app("org.signal"), app("com.mail"), app("com.mail", "Compose"), app("com.notes"))

    @Test fun newestFirstOnlyLaunchableApps() {
        val used = mapOf("org.signal" to 300L, "com.vivaldi.browser" to 500L, "com.android.nolauncher" to 900L, "com.mail" to 100L)
        assertEquals(
            listOf("com.vivaldi.browser" to 500L, "org.signal" to 300L, "com.mail" to 100L),
            RecentApps.pick(used, apps, exclude = emptySet(), hidden = emptyList()).map { it.app.packageName to it.lastUsed },
        )
    }

    @Test fun anAppWithTwoIconsAppearsOnce() {
        val r = RecentApps.pick(mapOf("com.mail" to 1L), apps, emptySet(), emptyList())
        assertEquals(listOf("com.mail/.Main"), r.map { it.app.id })
    }

    @Test fun leavesOutExcludedAndHidden() {
        val used = mapOf("org.signal" to 3L, "com.vivaldi.browser" to 2L, "com.notes" to 1L)
        val r = RecentApps.pick(used, apps, exclude = setOf("org.signal"), hidden = listOf("com.notes"))
        assertEquals(listOf("com.vivaldi.browser"), r.map { it.app.packageName })
    }

    @Test fun capsAtTheLimit() {
        val used = mapOf("org.signal" to 3L, "com.vivaldi.browser" to 2L, "com.notes" to 1L)
        assertEquals(2, RecentApps.pick(used, apps, emptySet(), emptyList(), limit = 2).size)
    }

    @Test fun hidingAndShowingAgain() {
        val s = UsageState().hideRecent("a").hideRecent("b").hideRecent("a")
        assertEquals(listOf("b", "a"), s.hiddenRecent)
        assertEquals(emptyList<String>(), s.showAllRecent().hiddenRecent)
    }
}
