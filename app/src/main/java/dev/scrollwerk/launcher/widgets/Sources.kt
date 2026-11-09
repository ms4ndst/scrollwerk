package dev.scrollwerk.launcher.widgets

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.CalendarContract
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// ---------------------------------------------------------------- media

/**
 * Exists only so the user can grant "notification access", which is what Android requires
 * before an app may read other apps' media sessions. It never reads notifications.
 */
class MediaListenerService : NotificationListenerService()

data class NowPlaying(
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

object MediaSessions {
    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun accessSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, MediaListenerService::class.java).flattenToString(),
            )

    /** The most relevant active session (a playing one wins), or null. */
    fun nowPlaying(context: Context): Flow<NowPlaying?> = callbackFlow {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, MediaListenerService::class.java)
        val handler = Handler(Looper.getMainLooper())
        var current: MediaController? = null

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { current?.let { trySend(describe(it)) } }
            override fun onPlaybackStateChanged(state: PlaybackState?) { current?.let { trySend(describe(it)) } }
            override fun onSessionDestroyed() { trySend(null) }
        }

        fun pick(controllers: List<MediaController>?) {
            val next = controllers.orEmpty().let { list ->
                list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            }
            if (next?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(callback)
                next?.registerCallback(callback, handler)
                current = next
            }
            trySend(next?.let(::describe))
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            pick(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            trySend(null) // access was revoked
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            current?.unregisterCallback(callback)
        }
    }.conflate()

    private fun describe(c: MediaController): NowPlaying? {
        val meta = c.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return NowPlaying(title, artist, art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c)
    }
}

// ---------------------------------------------------------------- agenda

data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int?,
)

object Agenda {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that haven't ended yet, within the next [days] days. */
    fun upcoming(context: Context, limit: Int = 4, days: Int = 2): List<AgendaEvent> {
        if (!hasPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 24 * 3_600_000L) // all-day events start at UTC midnight
            ContentUris.appendId(it, now + days * 24 * 3_600_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val events = mutableListOf<AgendaEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection,
                // Every calendar synced to the phone. Not VISIBLE: that's a calendar app's "show this calendar"
                // checkbox, and sync adapters such as DAVx⁵ often create calendars with it off.
                "${CalendarContract.Instances.SYNC_EVENTS} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val end = c.getLong(3)
                    if (end <= now) continue
                    events += AgendaEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = c.getLong(2),
                        end = end,
                        allDay = c.getInt(4) == 1,
                        color = if (c.isNull(5)) null else c.getInt(5),
                    )
                    if (events.size >= limit) break
                }
            }
        }
        return events
    }

    /**
     * Opens [event] in [calendarApp] (a package name; null = the system default). Apps that
     * can't show a single event (e.g. ones with their own sync) are simply launched.
     */
    fun openEvent(context: Context, event: AgendaEvent, calendarApp: String?) {
        val view = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (calendarApp != null) view.setPackage(calendarApp)
        if (view.resolveActivity(context.packageManager) != null && runCatching { context.startActivity(view) }.isSuccess) return
        openApp(context, calendarApp)
    }

    /** Opens the chosen calendar app, or the system's default calendar app. */
    fun openApp(context: Context, calendarApp: String?) {
        val intent = calendarApp?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Installed apps that declare themselves calendar apps or can show calendar events. */
    fun calendarApps(context: Context): List<CalendarApp> {
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 1)),
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI),
        )
        return probes.flatMap { probe -> runCatching { pm.queryIntentActivities(probe, 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .mapNotNull { pkg -> appLabel(context, pkg)?.let { CalendarApp(pkg, it) } }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}

data class CalendarApp(val packageName: String, val label: String)

// ---------------------------------------------------------------- system monitor

data class SystemStats(
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val batteryTempC: Float?,
) {
    val ramFraction get() = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
    val storageFraction get() = if (storageTotal > 0) storageUsed.toFloat() / storageTotal else 0f
}

object SystemMonitor {
    /** Polls while collected; collect with collectAsStateWithLifecycle so it stops off-screen. */
    fun stats(context: Context, periodMs: Long = 5_000): Flow<SystemStats> = flow {
        while (true) {
            emit(read(context))
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)

    fun read(context: Context): SystemStats {
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val fs = StatFs(Environment.getDataDirectory().path)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) // sticky, no receiver
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        return SystemStats(
            ramUsed = mem.totalMem - mem.availMem,
            ramTotal = mem.totalMem,
            storageUsed = fs.totalBytes - fs.availableBytes,
            storageTotal = fs.totalBytes,
            batteryTempC = temp?.div(10f),
        )
    }
}

// ---------------------------------------------------------------- flashlight

class Flashlight(context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    val available get() = cameraId != null

    fun state(): Flow<Boolean> = callbackFlow {
        val id = cameraId
        if (id == null) { trySend(false); awaitClose { }; return@callbackFlow }
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(changedId: String, enabled: Boolean) { if (changedId == id) trySend(enabled) }
            override fun onTorchModeUnavailable(changedId: String) { if (changedId == id) trySend(false) }
        }
        camera.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
        awaitClose { camera.unregisterTorchCallback(cb) }
    }

    fun set(on: Boolean) {
        cameraId?.let { runCatching { camera.setTorchMode(it, on) } }
    }
}
