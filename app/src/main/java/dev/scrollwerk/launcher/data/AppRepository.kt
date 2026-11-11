package dev.scrollwerk.launcher.data

import android.content.Context
import android.content.Intent
import android.os.Process
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.pm.ShortcutInfo
import dev.scrollwerk.launcher.search.ShortcutEntry
import android.os.Build
import androidx.core.graphics.createBitmap
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

/** Colours (ARGB) for themed icons. */
data class IconTint(val foreground: Int, val background: Int)

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

    /**
     * The app's icon. With a [tint], it's drawn in the theme's colours instead (see [themedIcon]).
     */
    suspend fun icon(id: String, tint: IconTint? = null): ImageBitmap? {
        val key = if (tint == null) id else "$id|${tint.foreground}|${tint.background}"
        return icons[key] ?: withContext(Dispatchers.IO) {
            val info = infos[id] ?: return@withContext null
            val bitmap = tint?.let { t -> runCatching { info.getIcon(0)?.let { themedIcon(it, info.user, t) } }.getOrNull() }
                ?: info.getBadgedIcon(0)?.toBitmap(iconSizePx, iconSizePx)
                ?: return@withContext null
            bitmap.asImageBitmap().also { icons[key] = it }
        }
    }

    /**
     * The app's glyph in [tint]'s foreground on a rounded tile of its background: the monochrome
     * layer when the app ships one (Android 13+), else the glyph pulled out of its normal icon.
     */
    private fun themedIcon(source: Drawable, user: UserHandle, tint: IconTint): Bitmap {
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) (source as? AdaptiveIconDrawable)?.monochrome?.mutate() else null
        val s = iconSizePx
        val bitmap = createBitmap(s, s)
        val canvas = Canvas(bitmap)
        val radius = s * 0.3f
        canvas.clipPath(Path().apply { addRoundRect(0f, 0f, s.toFloat(), s.toFloat(), radius, radius, Path.Direction.CW) })
        canvas.drawColor(tint.background)
        if (mono != null) {
            // Adaptive layers are 108 units with the visible 72 in the middle: draw them 1.5× the tile.
            mono.setTint(tint.foreground)
            mono.setBounds(-s / 4, -s / 4, s + s / 4, s + s / 4)
            mono.draw(canvas)
        } else {
            val pixels = IntArray(s * s)
            source.toBitmap(s, s, Bitmap.Config.ARGB_8888).getPixels(pixels, 0, s, 0, 0, s, s)
            val mask = IconGlyph.alphaMask(pixels)
            val rgb = tint.foreground and 0xFFFFFF
            val fgAlpha = tint.foreground ushr 24
            val glyph = Bitmap.createBitmap(IntArray(s * s) { (mask[it] * fgAlpha / 255 shl 24) or rgb }, s, s, Bitmap.Config.ARGB_8888)
            // Legacy icons fill their square; adaptive ones already sit inside their mask.
            val inset = s * if (source is AdaptiveIconDrawable) 0.06f else 0.16f
            canvas.drawBitmap(glyph, null, RectF(inset, inset, s - inset, s - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (user == Process.myUserHandle()) return bitmap
        // Keep the work-profile badge.
        return appContext.packageManager.getUserBadgedIcon(BitmapDrawable(appContext.resources, bitmap), user).toBitmap(s, s)
    }

    // ------------------------------------------------------------ app shortcuts

    private val shortcutInfos = ConcurrentHashMap<String, ShortcutInfo>()

    /**
     * Every enabled app shortcut (static and dynamic), now and after any change. Empty unless
     * Scrollwerk is the default home app: Android only shows shortcuts to that one.
     */
    fun shortcuts(): Flow<List<ShortcutEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            private fun changed() { trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = changed()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(Unit)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate().map { loadShortcuts() }.flowOn(Dispatchers.IO)

    private fun loadShortcuts(): List<ShortcutEntry> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val fresh = LinkedHashMap<String, Pair<ShortcutEntry, ShortcutInfo>>()
        userManager.userProfiles.forEach { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
                .filter { it.isEnabled && it.`package` != ownPackage }
                .forEach { info ->
                    val appId = appIdFor(info, user, serial) ?: return@forEach
                    val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                    val id = "${info.`package`}/${info.id}#$serial"
                    fresh[id] = ShortcutEntry(id, appId, info.`package`, label) to info
                }
        }
        shortcutInfos.keys.retainAll(fresh.keys)
        fresh.forEach { (id, pair) -> shortcutInfos[id] = pair.second }
        return fresh.values.map { it.first }
    }

    /** The launcher entry a shortcut belongs to: its own activity, else the app's first launchable one. */
    private fun appIdFor(info: ShortcutInfo, user: UserHandle, serial: Long): String? {
        // Asked directly rather than from [infos], which may not have loaded yet.
        val activities = runCatching { launcherApps.getActivityList(info.`package`, user) }.getOrNull().orEmpty()
        val owner = activities.firstOrNull { it.componentName == info.activity } ?: activities.firstOrNull() ?: return null
        return "${owner.componentName.flattenToShortString()}#$serial"
    }

    suspend fun shortcutIcon(id: String, tint: IconTint? = null): ImageBitmap? {
        val key = "shortcut:$id|${tint?.foreground}|${tint?.background}"
        return icons[key] ?: withContext(Dispatchers.IO) {
            val info = shortcutInfos[id] ?: return@withContext null
            val density = appContext.resources.displayMetrics.densityDpi
            val drawable = runCatching { launcherApps.getShortcutIconDrawable(info, density) }.getOrNull() ?: return@withContext null
            val bitmap = tint?.let { runCatching { themedIcon(drawable, info.userHandle, it) }.getOrNull() }
                ?: drawable.toBitmap(iconSizePx, iconSizePx)
            bitmap.asImageBitmap().also { icons[key] = it }
        }
    }

    /** Returns false if the shortcut vanished or its app refused it. */
    fun launchShortcut(id: String): Boolean {
        val info = shortcutInfos[id] ?: return false
        return runCatching { launcherApps.startShortcut(info, null, null) }.isSuccess
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
