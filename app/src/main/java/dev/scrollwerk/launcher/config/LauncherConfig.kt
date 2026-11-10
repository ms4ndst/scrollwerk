package dev.scrollwerk.launcher.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User-owned config file, in the spirit of niri's and Noctalia's: written once with every
 * default filled in, re-read on every resume, and a broken edit never replaces the last
 * good config (see [ConfigStore]).
 */
@Serializable
data class LauncherConfig(
    val theme: Theme = Theme(),
    val bar: Bar = Bar(),
    val launcher: Launcher = Launcher(),
    val widgets: Widgets = Widgets(),
    val notifications: Notifications = Notifications(),
) {
    @Serializable
    data class Theme(
        /**
         * A Noctalia palette name (Noctalia, Catppuccin, Dracula, Gruvbox, Nord, Rosé Pine,
         * Tokyo-Night, Kanagawa, Ayu, Eldritch), or "wallpaper" to derive colours from the wallpaper.
         * The Control Center's theme switcher writes this field.
         */
        val palette: String = "Noctalia",
        val mode: ThemeMode = ThemeMode.Dark,
        /** Multiplies Noctalia's corner radii (3/6/9/12 dp). */
        val cornerRadiusScale: Float = 1f,
        /** Replace the home-screen wallpaper with the palette's generated one when the theme changes. */
        val matchingWallpaper: Boolean = false,
    )

    @Serializable
    data class Bar(
        /** Hide Android's status bar on the home screen; swipe down from the top to peek. */
        val hideSystemStatusBar: Boolean = true,
        /** Detached from the screen edges with rounded corners, vs. a full-width strip. */
        val floating: Boolean = true,
        /** null = follow the system 12/24-hour setting. */
        val use24Hour: Boolean? = null,
        val showDate: Boolean = true,
    )

    @Serializable
    data class Launcher(
        /** "bottom" keeps the prompt next to the keyboard; best match sits right above it. */
        val searchPosition: SearchPosition = SearchPosition.Bottom,
        /** Frequently launched apps rank higher in search results. */
        val sortByUsage: Boolean = true,
        /** Category chips (All, Recent, Games, Social, ...). */
        val categories: Boolean = true,
        /** Show a result row for arithmetic such as `12*7`; activating it copies the result. */
        val calculator: Boolean = true,
    )

    @Serializable
    data class Widgets(
        val clock: Boolean = true,
        val weather: Boolean = true,
        val media: Boolean = true,
        val agenda: Boolean = true,
        val system: Boolean = true,
        /** City name for weather, e.g. "Stockholm"; null = use the phone's location. */
        val weatherLocation: String? = null,
        val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
        /** Package of the app the agenda opens events in; null = the system default calendar app. */
        val calendarApp: String? = null,
    )

    @Serializable
    data class Notifications(
        /** Show the notification bar under the top bar. Needs "notification access". */
        val enabled: Boolean = false,
        val slack: Boolean = true,
        /** Notifications from the default SMS app. */
        val sms: Boolean = true,
        val email: Boolean = true,
        /** Package of the email app to watch and open; null = any installed email app. */
        val emailApp: String? = null,
    )

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true // configs from other versions still load
            allowComments = true
            allowTrailingComma = true
        }

        fun parse(text: String): Result<LauncherConfig> =
            runCatching { json.decodeFromString(serializer(), text) }

        fun encode(config: LauncherConfig): String = json.encodeToString(serializer(), config)
    }
}

@Serializable
enum class ThemeMode {
    @SerialName("dark") Dark,
    @SerialName("light") Light,
    @SerialName("system") System,
}

@Serializable
enum class TemperatureUnit {
    @SerialName("celsius") Celsius,
    @SerialName("fahrenheit") Fahrenheit,
}

@Serializable
enum class SearchPosition {
    @SerialName("bottom") Bottom,
    @SerialName("top") Top,
}
