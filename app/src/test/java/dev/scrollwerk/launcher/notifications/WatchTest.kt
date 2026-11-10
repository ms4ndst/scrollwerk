package dev.scrollwerk.launcher.notifications

import dev.scrollwerk.launcher.config.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {
    private val on = LauncherConfig.Notifications(enabled = true)
    private val sms = listOf("com.google.android.apps.messaging")
    private val email = listOf("com.google.android.gm", "com.fsck.k9")

    @Test fun offByDefaultAndWatchesNothing() {
        assertTrue(Watch.build(LauncherConfig.Notifications(), sms, email).isEmpty)
    }

    @Test fun anyEmailAppWhenNoneChosen() {
        val w = Watch.build(on, sms, email)
        assertEquals(Source.Slack, w[SLACK_PACKAGE])
        assertEquals(Source.Sms, w["com.google.android.apps.messaging"])
        assertEquals(Source.Email, w["com.google.android.gm"])
        assertEquals(Source.Email, w["com.fsck.k9"])
        assertNull(w["com.whatsapp"])
    }

    @Test fun chosenEmailClientIsTheOnlyEmailSource() {
        val w = Watch.build(on.copy(emailApp = "com.fsck.k9"), sms, email)
        assertEquals(Source.Email, w["com.fsck.k9"])
        assertNull(w["com.google.android.gm"])
    }

    @Test fun disabledSourcesAreDropped() {
        val w = Watch.build(on.copy(slack = false, email = false), sms, email)
        assertEquals(setOf("com.google.android.apps.messaging"), w.sources.keys)
    }

    @Test fun smsWinsWhenAnAppIsBoth() {
        val both = "org.example.allinone"
        assertEquals(Source.Sms, Watch.build(on, listOf(both), listOf(both))[both])
    }

    @Test fun ageIsCompact() {
        val now = 10L * 24 * 3_600_000
        assertEquals("now", ageText(now, now - 30_000))
        assertEquals("now", ageText(now, now + 5_000)) // clock skew
        assertEquals("5m", ageText(now, now - 5 * 60_000))
        assertEquals("3h", ageText(now, now - 3 * 3_600_000))
        assertEquals("2d", ageText(now, now - 2 * 24 * 3_600_000))
    }
}
