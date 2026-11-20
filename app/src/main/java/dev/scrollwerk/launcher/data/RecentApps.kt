package dev.scrollwerk.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/** An app you used recently, and when it was last in front. */
data class RecentApp(val app: AppEntry, val lastUsed: Long)

/**
 * Recently used apps for the Overview, from Android's usage stats ("Usage access"). Android doesn't
 * let ordinary apps see or close other apps' windows, so this is when each app was last in front,
 * not a live list of open windows; tapping one brings its window back the way the app left it.
 */
object RecentApps {
    const val LIMIT = 10
    /** How far back to look. */
    const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** The Usage access page, on Scrollwerk's own switch where the phone supports that. */
    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Package → when it last came to the front, over [WINDOW_MS]. Empty without access. Call off the main thread. */
    fun lastUsed(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> = runCatching {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(now - WINDOW_MS, now)
        val out = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) out[e.packageName] = e.timeStamp
        }
        out
    }.getOrDefault(emptyMap())

    /**
     * The row: launchable apps by when they were last used, newest first. Leaves out [exclude]
     * (Scrollwerk, home screens, the phone's own recents screen) and the ones you hid.
     */
    fun pick(
        lastUsed: Map<String, Long>,
        apps: List<AppEntry>,
        exclude: Set<String>,
        hidden: Collection<String>,
        limit: Int = LIMIT,
    ): List<RecentApp> {
        // One entry per package: an app with several launcher icons appears once.
        val byPackage = LinkedHashMap<String, AppEntry>()
        apps.forEach { byPackage.putIfAbsent(it.packageName, it) }
        return lastUsed.entries
            .filter { (pkg, _) -> pkg !in exclude && pkg !in hidden && pkg in byPackage }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, time) -> RecentApp(byPackage.getValue(pkg), time) }
    }

    /** Packages that are never "recent apps": us, every home screen (incl. the system's recents screen), the system UI. */
    fun alwaysExcluded(context: Context): Set<String> {
        val homes = runCatching {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return homes.toSet() + context.packageName + "com.android.systemui"
    }
}
