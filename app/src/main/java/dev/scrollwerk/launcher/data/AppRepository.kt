package dev.scrollwerk.launcher.data

import android.content.Context
import android.content.Intent
import android.os.Process
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Launchable activities across all user profiles (personal + work), via [LauncherApps]. */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ownPackage = context.packageName
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconSizePx = (48 * context.resources.displayMetrics.density).toInt()

    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    /** Emits the full app list now and again after every install/remove/update. */
    fun apps(): Flow<List<AppEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            private fun changed() { trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(Unit)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate().map { load() }.flowOn(Dispatchers.IO)

    private fun load(): List<AppEntry> {
        val fresh = userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != ownPackage }
                .map { info -> "${info.componentName.flattenToShortString()}#$serial" to info }
        }.toMap()
        infos.keys.retainAll(fresh.keys)
        icons.clear() // icons can change on update; they reload lazily
        infos.putAll(fresh)
        return fresh.map { (id, info) ->
            AppEntry(id, info.label.toString(), info.componentName.packageName, categoryName(info.applicationInfo.category))
        }
    }

    private fun categoryName(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
        else -> null
    }

    suspend fun icon(id: String): ImageBitmap? = icons[id] ?: withContext(Dispatchers.IO) {
        infos[id]?.getBadgedIcon(0)
            ?.toBitmap(iconSizePx, iconSizePx)
            ?.asImageBitmap()
            ?.also { icons[id] = it }
    }

    /** Returns false if the app vanished or refused to start. */
    fun launch(id: String, sourceBounds: Rect? = null): Boolean {
        val info = infos[id] ?: return false
        return runCatching { launcherApps.startMainActivity(info.componentName, info.user, sourceBounds, null) }.isSuccess
    }

    /**
     * Opens two apps in split screen, like a niri column with two windows: [first] normally,
     * then [second] with FLAG_ACTIVITY_LAUNCH_ADJACENT once the first is in front.
     * Work-profile apps can only be started via LauncherApps, which can't ask for adjacency,
     * so for those the second app simply opens on top.
     */
    fun launchSplit(first: String, second: String): Boolean {
        if (!launch(first)) return launch(second)
        mainHandler.postDelayed({ launchAdjacent(second) }, SPLIT_DELAY_MS)
        return true
    }

    private fun launchAdjacent(id: String) {
        val info = infos[id] ?: return
        if (info.user != Process.myUserHandle()) { launch(id); return }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(info.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { appContext.startActivity(intent) }
    }

    fun openAppInfo(id: String) {
        val info = infos[id] ?: return
        runCatching { launcherApps.startAppDetailsActivity(info.componentName, info.user, null, null) }
    }

    private companion object {
        /** Time for the first app's window to come up before asking for the adjacent one. */
        const val SPLIT_DELAY_MS = 450L
    }
}
