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
        assertEquals(Source.WhatsApp, w["com.whatsapp"])
        assertEquals(Source.WhatsApp, w["com.whatsapp.w4b"])
        assertEquals(Source.Signal, w["org.thoughtcrime.securesms"])
        assertEquals(Source.Telegram, w["org.telegram.messenger"])
        assertNull(w["com.example.other"])
    }

    @Test fun chatAppsCanBeSwitchedOff() {
        val w = Watch.build(on.copy(whatsapp = false, telegram = false), sms, email)
        assertNull(w["com.whatsapp"])
        assertNull(w["org.telegram.messenger"])
        assertEquals(Source.Signal, w["org.thoughtcrime.securesms"])
    }

    @Test fun anAppsOwnSourceWinsOverSms() {
        // Signal used to be an SMS app too.
        assertEquals(Source.Signal, Watch.build(on, listOf("org.thoughtcrime.securesms"), email)["org.thoughtcrime.securesms"])
    }

    @Test fun everySourceHasItsOwnSwitch() {
        Source.entries.forEach { s ->
            assertTrue(on.with(s, true).isOn(s))
            assertTrue(!on.with(s, false).isOn(s))
            assertEquals(Source.entries.filter { it != s }.map { on.isOn(it) }, Source.entries.filter { it != s }.map { on.with(s, false).isOn(it) })
        }
    }

    @Test fun chosenEmailClientIsTheOnlyEmailSource() {
        val w = Watch.build(on.copy(emailApp = "com.fsck.k9"), sms, email)
        assertEquals(Source.Email, w["com.fsck.k9"])
        assertNull(w["com.google.android.gm"])
    }

    @Test fun disabledSourcesAreDropped() {
        val w = Watch.build(on.copy(slack = false, signal = false, whatsapp = false, telegram = false, email = false), sms, email)
        assertEquals(setOf("com.google.android.apps.messaging"), w.sources.keys)
    }

    @Test fun smsWinsWhenAnAppIsBoth() {
        val both = "org.example.allinone"
        assertEquals(Source.Sms, Watch.build(on, listOf(both), listOf(both))[both])
    }

    @Test fun stripShowsOnlySourcesWithAlertsNewestFirst() {
        val enabled = Source.entries
        assertEquals(StripSources(emptyList(), 0), stripSources(enabled, emptyList(), null))
        val newest = listOf(Source.WhatsApp, Source.Email, Source.WhatsApp, Source.Slack)
        assertEquals(StripSources(listOf(Source.WhatsApp, Source.Email, Source.Slack), 0), stripSources(enabled, newest, null))
    }

    @Test fun stripOverflowsAfterThree() {
        val newest = listOf(Source.Signal, Source.Sms, Source.Telegram, Source.Email, Source.Slack)
        assertEquals(StripSources(listOf(Source.Signal, Source.Sms, Source.Telegram), 2), stripSources(Source.entries, newest, null))
    }

    @Test fun selectedSourceKeepsItsCapsule() {
        val newest = listOf(Source.Signal, Source.Sms, Source.Telegram, Source.Email)
        // Selected with alerts but beyond the first three: it takes the third place.
        assertEquals(StripSources(listOf(Source.Signal, Source.Sms, Source.Email), 1), stripSources(Source.entries, newest, Source.Email))
        // Selected without alerts: last, so it can be tapped off again.
        assertEquals(StripSources(listOf(Source.Signal, Source.Slack), 0), stripSources(Source.entries, listOf(Source.Signal), Source.Slack))
    }

    @Test fun stripIgnoresDisabledSources() {
        assertEquals(StripSources(listOf(Source.Email), 0), stripSources(listOf(Source.Email), listOf(Source.Slack, Source.Email), Source.Slack))
    }

    @Test fun ageIsCompact() {
        val now = 10L * 24 * 3_600_000
        assertEquals("now", ageText(now, now - 30_000))
        assertEquals("now", ageText(now, now + 5_000)) // clock skew
        assertEquals("5m", ageText(now, now - 5 * 60_000))
        assertEquals("3h", ageText(now, now - 3 * 3_600_000))
        assertEquals("2d", ageText(now, now - 2 * 24 * 3_600_000))
    }

    @Test fun badgesCountPerPackageAndTakeTheNewestSource() {
        val badges = badgesByPackage(listOf("slack" to Source.Slack, "mail" to Source.Email, "slack" to Source.Slack))
        assertEquals(Badge(2, Source.Slack), badges["slack"])
        assertEquals(Badge(1, Source.Email), badges["mail"])
    }

    @Test fun splitColumnBadgeAddsUpBothApps() {
        val badges = mapOf("slack" to Badge(2, Source.Slack), "mail" to Badge(3, Source.Email))
        assertEquals(Badge(5, Source.Slack), columnBadge(listOf("slack", "mail"), badges))
        assertEquals(Badge(3, Source.Email), columnBadge(listOf("calc", "mail"), badges))
        assertNull(columnBadge(listOf("calc"), badges))
    }
}
