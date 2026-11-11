package dev.scrollwerk.launcher.notifications

import dev.scrollwerk.launcher.config.LauncherConfig

/** Where an alert came from. The notification bar shows one capsule per enabled source. */
enum class Source(val label: String) {
    Slack("Slack"),
    Sms("SMS"),
    Email("Email"),
}

const val SLACK_PACKAGE = "com.Slack"

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
         * If one package matches several sources, Slack wins over SMS, and SMS over email.
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
            if (config.slack) map[SLACK_PACKAGE] = Source.Slack
            return Watch(map)
        }
    }
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
