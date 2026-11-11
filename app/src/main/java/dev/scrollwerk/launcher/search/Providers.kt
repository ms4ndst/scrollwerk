package dev.scrollwerk.launcher.search

/** A Settings screen the launcher can open. [action] is an `android.settings.*` intent action. */
data class SettingsPage(val label: String, val action: String, val keywords: List<String> = emptyList())

/** A launcher result with its fuzzy highlights; [T] is a settings page, a contact, ... */
data class Scored<T>(val item: T, val matched: IntArray, val score: Int) {
    override fun equals(other: Any?) = other is Scored<*> && item == other.item && matched.contentEquals(other.matched)
    override fun hashCode() = 31 * item.hashCode() + matched.contentHashCode()
}

/** "wifi" → Wi-Fi settings. Fuzzy on the label, else a keyword that starts with the query. */
object SettingsSearch {
    const val MIN_QUERY = 2
    const val LIMIT = 3
    private const val KEYWORD_SCORE = 10

    val pages = listOf(
        SettingsPage("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wlan", "network", "internet")),
        SettingsPage("Bluetooth", "android.settings.BLUETOOTH_SETTINGS", listOf("pair", "headphones")),
        SettingsPage("Mobile network", "android.settings.DATA_ROAMING_SETTINGS", listOf("data", "sim", "roaming", "cellular", "4g", "5g")),
        SettingsPage("Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight", "flygplan")),
        SettingsPage("NFC", "android.settings.NFC_SETTINGS", listOf("tap", "pay")),
        SettingsPage("VPN", "android.settings.VPN_SETTINGS"),
        SettingsPage("Display", "android.settings.DISPLAY_SETTINGS", listOf("brightness", "screen", "font", "dark")),
        SettingsPage("Night Light", "android.settings.NIGHT_DISPLAY_SETTINGS", listOf("blue light", "warm")),
        SettingsPage("Sound", "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "vibrate")),
        SettingsPage("Notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("alerts")),
        SettingsPage("Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("power", "charging")),
        SettingsPage("Battery saver", "android.settings.BATTERY_SAVER_SETTINGS", listOf("power saving")),
        SettingsPage("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("space", "disk")),
        SettingsPage("Apps", "android.settings.APPLICATION_SETTINGS", listOf("applications", "uninstall")),
        SettingsPage("Default apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("home app", "browser")),
        SettingsPage("Location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
        SettingsPage("Security", "android.settings.SECURITY_SETTINGS", listOf("lock screen", "fingerprint", "password", "pin")),
        SettingsPage("Privacy", "android.settings.PRIVACY_SETTINGS", listOf("permissions")),
        SettingsPage("Accounts", "android.settings.SYNC_SETTINGS", listOf("sync", "google")),
        SettingsPage("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("talkback", "a11y")),
        SettingsPage("Language & input", "android.settings.LOCALE_SETTINGS", listOf("keyboard", "language", "språk")),
        SettingsPage("Date & time", "android.settings.DATE_SETTINGS", listOf("clock", "timezone")),
        SettingsPage("Cast", "android.settings.CAST_SETTINGS", listOf("chromecast", "screen mirroring")),
        SettingsPage("Developer options", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("usb debugging", "adb")),
        SettingsPage("About phone", "android.settings.DEVICE_INFO_SETTINGS", listOf("version", "build number", "model")),
        SettingsPage("System update", "android.settings.SYSTEM_UPDATE_SETTINGS", listOf("update", "upgrade")),
    )

    fun search(query: String, all: List<SettingsPage> = pages, limit: Int = LIMIT): List<Scored<SettingsPage>> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        return all.mapNotNull { page ->
            FuzzyMatcher.match(q, page.label)?.let { return@mapNotNull Scored(page, it.positions, it.score) }
            if (page.keywords.any { it.startsWith(q, ignoreCase = true) }) Scored(page, IntArray(0), KEYWORD_SCORE) else null
        }.sortedByDescending { it.score }.take(limit)
    }
}

/** A phone contact. [lookupUri] opens it in the contacts app. */
data class ContactEntry(val id: Long, val name: String, val lookupUri: String, val hasPhone: Boolean)

object ContactSearch {
    const val MIN_QUERY = 2
    const val LIMIT = 4

    fun search(contacts: List<ContactEntry>, query: String, limit: Int = LIMIT): List<Scored<ContactEntry>> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        return contacts.mapNotNull { c -> FuzzyMatcher.match(q, c.name)?.let { Scored(c, it.positions, it.score) } }
            .sortedWith(compareByDescending<Scored<ContactEntry>> { it.score }.thenBy { it.item.name })
            .take(limit)
    }
}

object WebSearch {
    /** [template] has `%s` where the encoded query goes, e.g. "https://duckduckgo.com/?q=%s". */
    fun url(template: String, query: String): String =
        template.replace("%s", java.net.URLEncoder.encode(query.trim(), "UTF-8"))
}
