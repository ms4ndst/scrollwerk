package dev.scrollwerk.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.widgets.AppChoice
import dev.scrollwerk.launcher.widgets.MediaSessions
import dev.scrollwerk.launcher.widgets.appLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One notification shown in the notification bar. */
data class Alert(
    val key: String,
    val source: Source,
    val packageName: String,
    val title: String?,
    val text: String?,
    val time: Long,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
)

/**
 * The alerts the notification bar shows: Slack, SMS and email notifications that are still
 * in the shade, newest first. Fed by the notification listener; filtered by [configure].
 * All calls happen on the main thread (listener callbacks are delivered there).
 */
object NotificationFeed {
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private var watch = Watch.NONE
    private var listener: NotificationListenerService? = null

    fun hasAccess(context: Context) = MediaSessions.hasAccess(context)

    fun accessSettingsIntent(context: Context) = MediaSessions.accessSettingsIntent(context)

    /** Re-reads which packages count as Slack / SMS / email. Cheap to call on every resume. */
    fun configure(context: Context, config: LauncherConfig.Notifications) {
        val next = if (!config.enabled) Watch.NONE else Watch.build(
            config,
            smsPackages = smsPackages(context),
            emailPackages = if (config.emailApp == null) EmailApps.installed(context).map { it.packageName } else emptyList(),
        )
        if (next == watch) return
        watch = next
        rebuild()
    }

    internal fun attach(service: NotificationListenerService) {
        listener = service
        rebuild()
    }

    internal fun detach(service: NotificationListenerService) {
        if (listener !== service) return
        listener = null
        _alerts.value = emptyList()
    }

    internal fun posted(sbn: StatusBarNotification) {
        val alert = toAlert(sbn)
        _alerts.update { list ->
            val rest = list.filter { it.key != sbn.key }
            if (alert == null) rest else (rest + alert).sortedByDescending { it.time }
        }
    }

    internal fun removed(sbn: StatusBarNotification) {
        _alerts.update { list -> list.filter { it.key != sbn.key } }
    }

    /** Removes the notification from the phone too, as swiping it away in the shade would. */
    fun dismiss(alert: Alert) {
        runCatching { listener?.cancelNotification(alert.key) }
        _alerts.update { list -> list.filter { it.key != alert.key } }
    }

    fun dismissAll(alerts: List<Alert>) = alerts.forEach(::dismiss)

    /** Opens what the notification points at (the message, the thread), else the app itself. */
    fun open(context: Context, alert: Alert) {
        val sent = alert.contentIntent?.let { pi ->
            runCatching { pi.send(context, 0, null, null, null, null, allowActivityStart()) }.isSuccess
        } ?: false
        if (!sent) launchPackage(context, alert.packageName)
        if (sent && alert.autoCancel) dismiss(alert)
    }

    /** Opens the app behind a source: Slack, the default SMS app, or the chosen email client. */
    fun openSource(context: Context, source: Source, config: LauncherConfig.Notifications) {
        when (source) {
            Source.Slack -> launchPackage(context, SLACK_PACKAGE)
            Source.Sms -> Telephony.Sms.getDefaultSmsPackage(context)?.let { launchPackage(context, it) }
                ?: start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
            Source.Email -> EmailApps.open(context, config.emailApp)
        }
    }

    private fun rebuild() {
        val l = listener
        _alerts.value = if (l == null || watch.isEmpty) emptyList()
        else runCatching { l.activeNotifications }.getOrNull().orEmpty()
            .mapNotNull(::toAlert)
            .sortedByDescending { it.time }
    }

    private fun toAlert(sbn: StatusBarNotification): Alert? {
        val source = watch[sbn.packageName] ?: return null
        val n = sbn.notification
        // Group summaries repeat their children; ongoing ones are "syncing…" and the like.
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null
        val e = n.extras
        val title = (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: e.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val text = (e.getCharSequence(Notification.EXTRA_TEXT) ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (title == null && text == null) return null
        return Alert(
            key = sbn.key,
            source = source,
            packageName = sbn.packageName,
            title = title,
            text = text,
            time = sbn.postTime,
            contentIntent = n.contentIntent,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    /** The default SMS app; without one, every app that can send SMS. */
    private fun smsPackages(context: Context): List<String> =
        Telephony.Sms.getDefaultSmsPackage(context)?.let(::listOf)
            ?: runCatching {
                context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), 0)
                    .map { it.activityInfo.packageName }.distinct()
            }.getOrDefault(emptyList())

    /** Lets the other app's PendingIntent start its activity from our foreground launcher (Android 14+). */
    @Suppress("DEPRECATION")
    private fun allowActivityStart() = ActivityOptions.makeBasic().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
    }.toBundle()

    internal fun launchPackage(context: Context, packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { start(context, it) }
    }

    internal fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Installed email clients, for the email-client chooser. */
object EmailApps {
    fun installed(context: Context): List<AppChoice> {
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
        )
        return probes.flatMap { probe -> runCatching { pm.queryIntentActivities(probe, 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .mapNotNull { pkg -> appLabel(context, pkg)?.let { AppChoice(pkg, it) } }
            .sortedBy { it.label.lowercase() }
    }

    /** Opens [emailApp] (a package name), or the system's default email app when null. */
    fun open(context: Context, emailApp: String?) {
        val intent = emailApp?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        NotificationFeed.start(context, intent)
    }
}
