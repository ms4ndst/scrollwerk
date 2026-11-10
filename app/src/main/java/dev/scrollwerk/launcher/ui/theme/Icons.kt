package dev.scrollwerk.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons in the stroke style Noctalia uses (24-unit grid, 2-unit round strokes). */
object NIcons {
    val Search = icon("search", "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-6 -6")
    val Apps = icon(
        "apps",
        "M4 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z",
        "M14 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z",
        "M4 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z",
        "M14 17h6", "M17 14v6",
    )
    val Bolt = icon("bolt", "M13 3v7h6l-8 11v-7h-6l8 -11")
    val Pin = icon("pin", "M15 4.5l-4 4l-4 1.5l-1.5 1.5l7 7l1.5 -1.5l1.5 -4l4 -4", "M9 15l-4.5 4.5", "M14.5 4l5.5 5.5")
    val Calculator = icon(
        "calculator",
        "M4 5a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z",
        "M8 7h8", "M8 12h.01", "M12 12h.01", "M16 12h.01", "M8 16h.01", "M12 16h.01", "M16 16h.01",
    )
    val Info = icon("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 9h.01", "M11 12h1v4h1")


    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Columns = icon("columns", "M4 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M14 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z")
    val Split = icon("split", "M4 5a1 1 0 0 1 1 -1h14a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-14a1 1 0 0 1 -1 -1z", "M4 12h16")
    val ArrowLeft = icon("arrow-left", "M5 12h14", "M5 12l6 6", "M5 12l6 -6")
    val ArrowRight = icon("arrow-right", "M5 12h14", "M13 18l6 -6", "M13 6l6 6")
    val Trash = icon("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12", "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3")

    // weather
    val Sun = icon(
        "sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4",
        "M2 12h2", "M20 12h2", "M4.9 19.1l1.4 -1.4", "M17.7 6.3l1.4 -1.4",
    )
    val Moon = icon("moon", "M12 3a6 6 0 0 0 9 9a9 9 0 1 1 -9 -9z")
    val Cloud = icon("cloud", "M7 18a4.5 4.5 0 0 1 -.5 -9a6 6 0 0 1 11.5 1.5a3.75 3.75 0 0 1 0 7.5z")
    val CloudSun = icon(
        "cloud-sun", "M8 3v1", "M3 8h1", "M4.5 4.5l.7 .7", "M11.5 4.5l-.7 .7", "M5.2 11a3.5 3.5 0 0 1 5.8 -3.6",
        "M9 20a4 4 0 0 1 -.5 -8a5 5 0 0 1 9.5 1.5a3.25 3.25 0 0 1 0 6.5z",
    )
    val Rain = icon("rain", "M7 14a4.5 4.5 0 0 1 -.5 -9a6 6 0 0 1 11.5 1.5a3.75 3.75 0 0 1 0 7.5z", "M8 17l-1 3", "M12 17l-1 3", "M16 17l-1 3")
    val Snow = icon("snow", "M7 14a4.5 4.5 0 0 1 -.5 -9a6 6 0 0 1 11.5 1.5a3.75 3.75 0 0 1 0 7.5z", "M8 18h.01", "M12 18h.01", "M16 18h.01", "M10 21h.01", "M14 21h.01")
    val Storm = icon("storm", "M7 14a4.5 4.5 0 0 1 -.5 -9a6 6 0 0 1 11.5 1.5a3.75 3.75 0 0 1 0 7.5z", "M12 15l-2 3.5h4l-2 3.5")
    val Fog = icon("fog", "M8 5h8", "M4 9h16", "M4 13h16", "M6 17h12", "M9 21h6")
    val MapPin = icon("map-pin", "M9 11a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M12 21l-5 -6.5a7 7 0 1 1 10 0z")

    // media
    val Music = icon("music", "M5 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M11 17v-13l8 2v3l-8 -2")
    val Play = icon("play", "M7 4v16l13 -8z")
    val Pause = icon("pause", "M6 5h4v14h-4z", "M14 5h4v14h-4z")
    val Next = icon("next", "M4 5v14l10 -7z", "M20 5v14")
    val Prev = icon("prev", "M20 5v14l-10 -7z", "M4 5v14")

    // agenda / system
    val Calendar = icon(
        "calendar", "M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z",
        "M16 3v4", "M8 3v4", "M4 11h16",
    )
    val Chip = icon(
        "chip", "M5 7a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2z", "M9 9h6v6h-6z",
        "M3 10h2", "M3 14h2", "M10 3v2", "M14 3v2", "M21 10h-2", "M21 14h-2", "M14 21v-2", "M10 21v-2",
    )

    // control center
    val Sliders = icon("sliders", "M4 6h8", "M16 6h4", "M14 4v4", "M4 12h2", "M10 12h10", "M8 10v4", "M4 18h11", "M19 18h1", "M17 16v4")
    val Flashlight = icon("flashlight", "M7 3h10v4l-2 3v11h-6v-11l-2 -3z", "M7 7h10", "M12 13v2")
    val Wifi = icon("wifi", "M12 18h.01", "M9.2 15.2a4 4 0 0 1 5.6 0", "M6.4 12.4a8 8 0 0 1 11.2 0", "M3.5 9.5a12 12 0 0 1 17 0")
    val Bluetooth = icon("bluetooth", "M7 8l10 8l-5 4v-16l5 4l-10 8")
    val Volume = icon("volume", "M4 10v4h3l5 4v-12l-5 4z", "M16 9a4 4 0 0 1 0 6", "M19 6a8 8 0 0 1 0 12")
    val Settings = icon(
        "settings", "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M12 2v3", "M12 19v3", "M2 12h3", "M19 12h3",
    )
    val Photo = icon(
        "photo", "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z",
        "M4 16l5 -5l4 4l2 -2l5 5", "M15 8h.01",
    )
    val Palette = icon(
        "palette", "M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.6 9 8a4 4 0 0 1 -4 4h-2.5a1.5 1.5 0 0 0 -1 2.6a1.5 1.5 0 0 1 -1.5 3.4",
        "M7.5 10.5h.01", "M12 7.5h.01", "M16.5 10.5h.01",
    )

    // notifications
    val Bell = icon(
        "bell", "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6",
        "M9 17v1a3 3 0 0 0 6 0v-1",
    )
    val Hash = icon("hash", "M5 9h14", "M5 15h14", "M11 4l-4 16", "M17 4l-4 16")
    val Message = icon(
        "message", "M8 9h8", "M8 13h6",
        "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3z",
    )
    val Mail = icon("mail", "M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2z", "M3 7l9 6l9 -6")
    val Close = icon("close", "M18 6l-12 12", "M6 6l12 12")
    val ChevronDown = icon("chevron-down", "M6 9l6 6l6 -6")
    val ChevronUp = icon("chevron-up", "M6 15l6 -6l6 6")

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black), // tinted by Icon()
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
