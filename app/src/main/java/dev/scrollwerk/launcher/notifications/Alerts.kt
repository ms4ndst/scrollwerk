package dev.scrollwerk.launcher.notifications

import dev.scrollwerk.launcher.config.LauncherConfig

const val SLACK_PACKAGE = "com.Slack"

/**
 * Where an alert came from. The notification bar shows one capsule per enabled source.
 * [apps] are the packages of a source that is one particular app (official builds and variants,
 * such as WhatsApp Business or Telegram X); SMS and email are whichever apps the phone uses.
 */
enum class Source(val label: String, val apps: List<String> = emptyList()) {
    Slack("Slack", listOf(SLACK_PACKAGE)),
    Signal("Signal", listOf("org.thoughtcrime.securesms")),
    WhatsApp("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    Telegram("Telegram", listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
    Claude("Claude", listOf("com.anthropic.claude")),
    Sms("SMS"),
    Email("Email"),
}

/** Whether the notification-bar config has [source] switched on. */
fun LauncherConfig.Notifications.isOn(source: Source) = when (source) {
    Source.Slack -> slack
    Source.Signal -> signal
    Source.WhatsApp -> whatsapp
    Source.Telegram -> telegram
    Source.Claude -> claude
    Source.Sms -> sms
    Source.Email -> email
}

/** [this] config with [source] switched [on] or off. */
fun LauncherConfig.Notifications.with(source: Source, on: Boolean) = when (source) {
    Source.Slack -> copy(slack = on)
    Source.Signal -> copy(signal = on)
    Source.WhatsApp -> copy(whatsapp = on)
    Source.Telegram -> copy(telegram = on)
    Source.Claude -> copy(claude = on)
    Source.Sms -> copy(sms = on)
    Source.Email -> copy(email = on)
}

/**
 * Which packages the notification bar listens to, and as what. Everything else is dropped
 * the moment it arrives; with the bar off, the map is empty and nothing is kept at all.
 */
data class Watch(val sources: Map<String, Source>) {
    val isEmpty get() = sources.isEmpty()

    operator fun get(packageName: String): Source? = sources[packageName]

    companion object {
        val NONE = Watch(emptyMap())

        /**
         * [smsPackages] is the default SMS app (or every SMS app if there's no default);
         * [emailPackages] is every installed email app, used when no email client is chosen.
         * If one package matches several sources, an app's own source (Slack, Signal, …) wins over
         * SMS, and SMS over email.
         */
        fun build(
            config: LauncherConfig.Notifications,
            smsPackages: Collection<String>,
            emailPackages: Collection<String>,
        ): Watch {
            if (!config.enabled) return NONE
            val map = LinkedHashMap<String, Source>()
            if (config.email) (config.emailApp?.let(::listOf) ?: emailPackages).forEach { map[it] = Source.Email }
            if (config.sms) smsPackages.forEach { map[it] = Source.Sms }
            Source.entries.filter { it.apps.isNotEmpty() && config.isOn(it) }
                .forEach { source -> source.apps.forEach { map[it] = source } }
            return Watch(map)
        }
    }
}

/** The source capsules on the strip: [shown] in order, and how many more sources with alerts didn't fit. */
data class StripSources(val shown: List<Source>, val more: Int)

/**
 * Only sources with alerts get a capsule, the one with the newest alert first, at most [max] of them.
 * The selected source keeps its capsule even with no alerts (so it can be tapped off again) and
 * always counts as one of the [max].
 * [newestFirst] is each alert's source, newest alert first.
 */
fun stripSources(enabled: Collection<Source>, newestFirst: List<Source>, selected: Source?, max: Int = 3): StripSources {
    val active = newestFirst.distinct().filter { it in enabled }
    val pinned = selected?.takeIf { it in enabled }
    val keep = active.filter { it != pinned }.take(if (pinned != null) max - 1 else max).toSet() + listOfNotNull(pinned)
    // In order of their newest alert; a selected source without alerts goes last.
    val shown = active.filter { it in keep } + listOfNotNull(pinned?.takeIf { it !in active })
    return StripSources(shown, active.count { it !in keep })
}

/** Compact age for the bar: "now", "5m", "3h", "2d". */
fun ageText(nowMs: Long, thenMs: Long): String {
    val minutes = ((nowMs - thenMs).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

/** An alert count for a workspace column; [source] (the newest alert's) picks the colour. */
data class Badge(val count: Int, val source: Source)

/** Alert counts per package, from (packageName, source) pairs ordered newest first. */
fun badgesByPackage(alerts: List<Pair<String, Source>>): Map<String, Badge> {
    val map = LinkedHashMap<String, Badge>()
    alerts.forEach { (pkg, source) ->
        map[pkg] = map[pkg]?.let { it.copy(count = it.count + 1) } ?: Badge(1, source)
    }
    return map
}

/** One badge for a column: a split column adds up both apps; the first app with alerts picks the colour. */
fun columnBadge(packages: List<String>, badges: Map<String, Badge>): Badge? {
    val found = packages.distinct().mapNotNull { badges[it] }
    if (found.isEmpty()) return null
    return Badge(found.sumOf { it.count }, found.first().source)
}
