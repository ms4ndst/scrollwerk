package dev.scrollwerk.launcher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// The first ten are generated from Noctalia's built-in palettes (src/theme/builtin_palettes.cpp, MIT License,
// Copyright (c) 2026 noctalia-dev); Everforest, Monokai Pro, One, Oxocarbon, Solarized and Synthwave '84 are
// mapped by hand from those schemes' published colours. Each palette has a dark and a light variant.

/** [font] / [headingFont] name fonts that override what [ThemeFonts] picks (set by imported palette files). */
class NamedPalette(
    val name: String,
    val dark: NoctaliaColors,
    val light: NoctaliaColors,
    val font: String? = null,
    val headingFont: String? = null,
)

object Palettes {
    val builtIn: List<NamedPalette> = listOf(
        NamedPalette(
            "Ayu",
            dark = NoctaliaColors(primary = Color(0xFFE6B450), onPrimary = Color(0xFF0B0E14), secondary = Color(0xFFAAD94C), onSecondary = Color(0xFF0B0E14), tertiary = Color(0xFF39BAE6), onTertiary = Color(0xFF0B0E14), error = Color(0xFFD95757), onError = Color(0xFF0B0E14), surface = Color(0xFF0B0E14), onSurface = Color(0xFFD1D1C7), surfaceContainer = Color(0xFF1E222A), onSurfaceVariant = Color(0xFF8E959E), outlineVariant = Color(0xFF565B66), shadow = Color(0xFF000000), hover = Color(0xFF39BAE6), onHover = Color(0xFF0B0E14)),
            light = NoctaliaColors(primary = Color(0xFFFF8F40), onPrimary = Color(0xFFF8F9FA), secondary = Color(0xFF86B300), onSecondary = Color(0xFFF8F9FA), tertiary = Color(0xFF55B4D4), onTertiary = Color(0xFFF8F9FA), error = Color(0xFFE65050), onError = Color(0xFFF8F9FA), surface = Color(0xFFF8F9FA), onSurface = Color(0xFF42474C), surfaceContainer = Color(0xFFE4E6E9), onSurfaceVariant = Color(0xFF6E757C), outlineVariant = Color(0xFF8A9199), shadow = Color(0xFFF8F9FA), hover = Color(0xFF55B4D4), onHover = Color(0xFFF8F9FA)),
        ),
        NamedPalette(
            "Catppuccin",
            dark = NoctaliaColors(primary = Color(0xFFCBA6F7), onPrimary = Color(0xFF11111B), secondary = Color(0xFFFAB387), onSecondary = Color(0xFF11111B), tertiary = Color(0xFF94E2D5), onTertiary = Color(0xFF11111B), error = Color(0xFFF38BA8), onError = Color(0xFF11111B), surface = Color(0xFF1E1E2E), onSurface = Color(0xFFCDD6F4), surfaceContainer = Color(0xFF313244), onSurfaceVariant = Color(0xFFA3B4EB), outlineVariant = Color(0xFF4C4F69), shadow = Color(0xFF11111B), hover = Color(0xFF94E2D5), onHover = Color(0xFF11111B)),
            light = NoctaliaColors(primary = Color(0xFF8839EF), onPrimary = Color(0xFFEFF1F5), secondary = Color(0xFFFE640B), onSecondary = Color(0xFFEFF1F5), tertiary = Color(0xFF40A02B), onTertiary = Color(0xFFEFF1F5), error = Color(0xFFD20F39), onError = Color(0xFFDCE0E8), surface = Color(0xFFEFF1F5), onSurface = Color(0xFF4C4F69), surfaceContainer = Color(0xFFCCD0DA), onSurfaceVariant = Color(0xFF6C6F85), outlineVariant = Color(0xFFA5ADCB), shadow = Color(0xFFDCE0E8), hover = Color(0xFF40A02B), onHover = Color(0xFFEFF1F5)),
        ),
        NamedPalette(
            "Dracula",
            dark = NoctaliaColors(primary = Color(0xFFBD93F9), onPrimary = Color(0xFF282A36), secondary = Color(0xFFFF79C6), onSecondary = Color(0xFF4E1D32), tertiary = Color(0xFF8BE9FD), onTertiary = Color(0xFF003543), error = Color(0xFFFF5555), onError = Color(0xFF282A36), surface = Color(0xFF282A36), onSurface = Color(0xFFF8F8F2), surfaceContainer = Color(0xFF44475A), onSurfaceVariant = Color(0xFFD6D8E0), outlineVariant = Color(0xFF5A5E77), shadow = Color(0xFF282A36), hover = Color(0xFF8BE9FD), onHover = Color(0xFF003543)),
            light = NoctaliaColors(primary = Color(0xFF8332F4), onPrimary = Color(0xFFFFFFFF), secondary = Color(0xFFFF1399), onSecondary = Color(0xFFFFFFFF), tertiary = Color(0xFF0398B9), onTertiary = Color(0xFFFFFFFF), error = Color(0xFFFF5555), onError = Color(0xFF282A36), surface = Color(0xFFF8F8F2), onSurface = Color(0xFF282A36), surfaceContainer = Color(0xFFE6E6EA), onSurfaceVariant = Color(0xFF44475A), outlineVariant = Color(0xFFCACAD3), shadow = Color(0xFFD6D8E0), hover = Color(0xFF0398B9), onHover = Color(0xFFFFFFFF)),
        ),
        NamedPalette(
            "Eldritch",
            dark = NoctaliaColors(primary = Color(0xFF37F499), onPrimary = Color(0xFF171928), secondary = Color(0xFF04D1F9), onSecondary = Color(0xFF171928), tertiary = Color(0xFFA48CF2), onTertiary = Color(0xFF171928), error = Color(0xFFF16C75), onError = Color(0xFF171928), surface = Color(0xFF212337), onSurface = Color(0xFFEBFAFA), surfaceContainer = Color(0xFF292E42), onSurfaceVariant = Color(0xFFABB4DA), outlineVariant = Color(0xFF3B4261), shadow = Color(0xFF414868), hover = Color(0xFFA48CF2), onHover = Color(0xFF171928)),
            light = NoctaliaColors(primary = Color(0xFFFB5BB6), onPrimary = Color(0xFF1E2029), secondary = Color(0xFF0AD6FF), onSecondary = Color(0xFF1E2029), tertiary = Color(0xFF8A69F7), onTertiary = Color(0xFF1E2029), error = Color(0xFFFB5B66), onError = Color(0xFF1E2029), surface = Color(0xFFF0F3F4), onSurface = Color(0xFF1E2029), surfaceContainer = Color(0xFFD5D9DB), onSurfaceVariant = Color(0xFF1E2029), outlineVariant = Color(0xFFC9CBCD), shadow = Color(0xFFE2E6E8), hover = Color(0xFF8A69F7), onHover = Color(0xFF1E2029)),
        ),
        NamedPalette(
            "Gruvbox",
            dark = NoctaliaColors(primary = Color(0xFFB8BB26), onPrimary = Color(0xFF282828), secondary = Color(0xFFFABD2F), onSecondary = Color(0xFF282828), tertiary = Color(0xFF83A598), onTertiary = Color(0xFF282828), error = Color(0xFFFB4934), onError = Color(0xFF282828), surface = Color(0xFF282828), onSurface = Color(0xFFFBF1C7), surfaceContainer = Color(0xFF3C3836), onSurfaceVariant = Color(0xFFEBDBB2), outlineVariant = Color(0xFF57514E), shadow = Color(0xFF282828), hover = Color(0xFF83A598), onHover = Color(0xFF282828)),
            light = NoctaliaColors(primary = Color(0xFF98971A), onPrimary = Color(0xFFFBF1C7), secondary = Color(0xFFD79921), onSecondary = Color(0xFFFBF1C7), tertiary = Color(0xFF458588), onTertiary = Color(0xFFFBF1C7), error = Color(0xFFCC241D), onError = Color(0xFFFBF1C7), surface = Color(0xFFFBF1C7), onSurface = Color(0xFF3C3836), surfaceContainer = Color(0xFFEBDBB2), onSurfaceVariant = Color(0xFF7C6F64), outlineVariant = Color(0xFFBDAE93), shadow = Color(0xFFD5C4A1), hover = Color(0xFF458588), onHover = Color(0xFFFBF1C7)),
        ),
        NamedPalette(
            "Kanagawa",
            dark = NoctaliaColors(primary = Color(0xFF76946A), onPrimary = Color(0xFF1F1F28), secondary = Color(0xFFC0A36E), onSecondary = Color(0xFF1F1F28), tertiary = Color(0xFF7E9CD8), onTertiary = Color(0xFF1F1F28), error = Color(0xFFC34043), onError = Color(0xFF1F1F28), surface = Color(0xFF1F1F28), onSurface = Color(0xFFC8C093), surfaceContainer = Color(0xFF2A2A37), onSurfaceVariant = Color(0xFF717C7C), outlineVariant = Color(0xFF363646), shadow = Color(0xFF1F1F28), hover = Color(0xFF7E9CD8), onHover = Color(0xFF1F1F28)),
            light = NoctaliaColors(primary = Color(0xFF6F894E), onPrimary = Color(0xFFF2ECBC), secondary = Color(0xFF77713F), onSecondary = Color(0xFFF2ECBC), tertiary = Color(0xFF4D699B), onTertiary = Color(0xFFF2ECBC), error = Color(0xFFC84053), onError = Color(0xFFF2ECBC), surface = Color(0xFFF2ECBC), onSurface = Color(0xFF545464), surfaceContainer = Color(0xFFE5DDB0), onSurfaceVariant = Color(0xFF8A8980), outlineVariant = Color(0xFFCFC49C), shadow = Color(0xFFF2ECBC), hover = Color(0xFF4D699B), onHover = Color(0xFFF2ECBC)),
        ),
        NamedPalette(
            "Noctalia",
            dark = NoctaliaColors(primary = Color(0xFFFFF59B), onPrimary = Color(0xFF0E0E43), secondary = Color(0xFFA9AEFE), onSecondary = Color(0xFF0E0E43), tertiary = Color(0xFF9BFECE), onTertiary = Color(0xFF0E0E43), error = Color(0xFFFD4663), onError = Color(0xFF0E0E43), surface = Color(0xFF070722), onSurface = Color(0xFFF3EDF7), surfaceContainer = Color(0xFF11112D), onSurfaceVariant = Color(0xFF7C80B4), outlineVariant = Color(0xFF21215F), shadow = Color(0xFF070722), hover = Color(0xFF9BFECE), onHover = Color(0xFF0E0E43)),
            light = NoctaliaColors(primary = Color(0xFF5D65F5), onPrimary = Color(0xFFDADCFF), secondary = Color(0xFF8E93D8), onSecondary = Color(0xFFDADCFF), tertiary = Color(0xFF0E0E43), onTertiary = Color(0xFFFEF29A), error = Color(0xFFFD4663), onError = Color(0xFF0E0E43), surface = Color(0xFFE6E8FA), onSurface = Color(0xFF0E0E43), surfaceContainer = Color(0xFFEFF0FF), onSurfaceVariant = Color(0xFF4B55C8), outlineVariant = Color(0xFF8288FC), shadow = Color(0xFFF3EDF7), hover = Color(0xFF0E0E43), onHover = Color(0xFFFEF29A)),
        ),
        NamedPalette(
            "Nord",
            dark = NoctaliaColors(primary = Color(0xFF8FBCBB), onPrimary = Color(0xFF2E3440), secondary = Color(0xFF88C0D0), onSecondary = Color(0xFF2E3440), tertiary = Color(0xFF5E81AC), onTertiary = Color(0xFF2E3440), error = Color(0xFFBF616A), onError = Color(0xFF2E3440), surface = Color(0xFF2E3440), onSurface = Color(0xFFECEFF4), surfaceContainer = Color(0xFF3B4252), onSurfaceVariant = Color(0xFFD8DEE9), outlineVariant = Color(0xFF505A70), shadow = Color(0xFF2E3440), hover = Color(0xFF5E81AC), onHover = Color(0xFF2E3440)),
            light = NoctaliaColors(primary = Color(0xFF5E81AC), onPrimary = Color(0xFFECEFF4), secondary = Color(0xFF64ADC2), onSecondary = Color(0xFFECEFF4), tertiary = Color(0xFF6FA9A8), onTertiary = Color(0xFFECEFF4), error = Color(0xFFBF616A), onError = Color(0xFFECEFF4), surface = Color(0xFFECEFF4), onSurface = Color(0xFF2E3440), surfaceContainer = Color(0xFFE5E9F0), onSurfaceVariant = Color(0xFF4C566A), outlineVariant = Color(0xFFC5CEDD), shadow = Color(0xFFD8DEE9), hover = Color(0xFF6FA9A8), onHover = Color(0xFFECEFF4)),
        ),
        NamedPalette(
            "Rosé Pine",
            dark = NoctaliaColors(primary = Color(0xFFEBBCBA), onPrimary = Color(0xFF191724), secondary = Color(0xFF9CCFD8), onSecondary = Color(0xFF191724), tertiary = Color(0xFF31748F), onTertiary = Color(0xFFE0DEF4), error = Color(0xFFEB6F92), onError = Color(0xFF191724), surface = Color(0xFF191724), onSurface = Color(0xFFE0DEF4), surfaceContainer = Color(0xFF26233A), onSurfaceVariant = Color(0xFF908CAA), outlineVariant = Color(0xFF403D52), shadow = Color(0xFF191724), hover = Color(0xFF524F67), onHover = Color(0xFFE0DEF4)),
            light = NoctaliaColors(primary = Color(0xFFD7827E), onPrimary = Color(0xFFFAF4ED), secondary = Color(0xFF56949F), onSecondary = Color(0xFFFAF4ED), tertiary = Color(0xFF286983), onTertiary = Color(0xFFFAF4ED), error = Color(0xFFB4637A), onError = Color(0xFFFAF4ED), surface = Color(0xFFFFFAF3), onSurface = Color(0xFF575279), surfaceContainer = Color(0xFFF2E9E1), onSurfaceVariant = Color(0xFF797593), outlineVariant = Color(0xFFDFDAD9), shadow = Color(0xFFFAF4ED), hover = Color(0xFFCECACD), onHover = Color(0xFF575279)),
        ),
        NamedPalette(
            "Tokyo-Night",
            dark = NoctaliaColors(primary = Color(0xFF7AA2F7), onPrimary = Color(0xFF16161E), secondary = Color(0xFFBB9AF7), onSecondary = Color(0xFF16161E), tertiary = Color(0xFF9ECE6A), onTertiary = Color(0xFF16161E), error = Color(0xFFF7768E), onError = Color(0xFF16161E), surface = Color(0xFF1A1B26), onSurface = Color(0xFFC0CAF5), surfaceContainer = Color(0xFF24283B), onSurfaceVariant = Color(0xFF9AA5CE), outlineVariant = Color(0xFF353D57), shadow = Color(0xFF15161E), hover = Color(0xFF9ECE6A), onHover = Color(0xFF16161E)),
            light = NoctaliaColors(primary = Color(0xFF2E7DE9), onPrimary = Color(0xFFE1E2E7), secondary = Color(0xFF9854F1), onSecondary = Color(0xFFE1E2E7), tertiary = Color(0xFF587539), onTertiary = Color(0xFFE1E2E7), error = Color(0xFFF52A65), onError = Color(0xFFE1E2E7), surface = Color(0xFFE1E2E7), onSurface = Color(0xFF3760BF), surfaceContainer = Color(0xFFD0D5E3), onSurfaceVariant = Color(0xFF6172B0), outlineVariant = Color(0xFFB4B5B9), shadow = Color(0xFFA8AECB), hover = Color(0xFF587539), onHover = Color(0xFFE1E2E7)),
        ),
        // Scrollwerk's own mappings of these schemes' published colours onto Noctalia's roles.
        NamedPalette(
            "Everforest",
            dark = NoctaliaColors(primary = Color(0xFFA7C080), onPrimary = Color(0xFF2D353B), secondary = Color(0xFFDBBC7F), onSecondary = Color(0xFF2D353B), tertiary = Color(0xFF83C092), onTertiary = Color(0xFF2D353B), error = Color(0xFFE67E80), onError = Color(0xFF2D353B), surface = Color(0xFF2D353B), onSurface = Color(0xFFD3C6AA), surfaceContainer = Color(0xFF343F44), onSurfaceVariant = Color(0xFF9DA9A0), outlineVariant = Color(0xFF475258), shadow = Color(0xFF232A2E), hover = Color(0xFF83C092), onHover = Color(0xFF2D353B)),
            light = NoctaliaColors(primary = Color(0xFF8DA101), onPrimary = Color(0xFFFDF6E3), secondary = Color(0xFFDFA000), onSecondary = Color(0xFFFDF6E3), tertiary = Color(0xFF35A77C), onTertiary = Color(0xFFFDF6E3), error = Color(0xFFF85552), onError = Color(0xFFFDF6E3), surface = Color(0xFFFDF6E3), onSurface = Color(0xFF5C6A72), surfaceContainer = Color(0xFFF4F0D9), onSurfaceVariant = Color(0xFF829181), outlineVariant = Color(0xFFE0DCC7), shadow = Color(0xFFEFEBD4), hover = Color(0xFF35A77C), onHover = Color(0xFFFDF6E3)),
        ),
        NamedPalette(
            "Solarized",
            dark = NoctaliaColors(primary = Color(0xFF268BD2), onPrimary = Color(0xFFFDF6E3), secondary = Color(0xFF2AA198), onSecondary = Color(0xFF002B36), tertiary = Color(0xFFB58900), onTertiary = Color(0xFF002B36), error = Color(0xFFDC322F), onError = Color(0xFFFDF6E3), surface = Color(0xFF002B36), onSurface = Color(0xFF93A1A1), surfaceContainer = Color(0xFF073642), onSurfaceVariant = Color(0xFF657B83), outlineVariant = Color(0xFF0E4553), shadow = Color(0xFF001E26), hover = Color(0xFF2AA198), onHover = Color(0xFF002B36)),
            light = NoctaliaColors(primary = Color(0xFF268BD2), onPrimary = Color(0xFFFDF6E3), secondary = Color(0xFF2AA198), onSecondary = Color(0xFFFDF6E3), tertiary = Color(0xFFCB4B16), onTertiary = Color(0xFFFDF6E3), error = Color(0xFFDC322F), onError = Color(0xFFFDF6E3), surface = Color(0xFFFDF6E3), onSurface = Color(0xFF586E75), surfaceContainer = Color(0xFFEEE8D5), onSurfaceVariant = Color(0xFF657B83), outlineVariant = Color(0xFFDDD6C1), shadow = Color(0xFFEEE8D5), hover = Color(0xFF2AA198), onHover = Color(0xFFFDF6E3)),
        ),
        NamedPalette(
            "One",
            dark = NoctaliaColors(primary = Color(0xFF61AFEF), onPrimary = Color(0xFF282C34), secondary = Color(0xFFC678DD), onSecondary = Color(0xFF282C34), tertiary = Color(0xFF98C379), onTertiary = Color(0xFF282C34), error = Color(0xFFE06C75), onError = Color(0xFF282C34), surface = Color(0xFF282C34), onSurface = Color(0xFFABB2BF), surfaceContainer = Color(0xFF31353F), onSurfaceVariant = Color(0xFF7F848E), outlineVariant = Color(0xFF3E4451), shadow = Color(0xFF21252B), hover = Color(0xFF56B6C2), onHover = Color(0xFF282C34)),
            light = NoctaliaColors(primary = Color(0xFF4078F2), onPrimary = Color(0xFFFAFAFA), secondary = Color(0xFFA626A4), onSecondary = Color(0xFFFAFAFA), tertiary = Color(0xFF50A14F), onTertiary = Color(0xFFFAFAFA), error = Color(0xFFE45649), onError = Color(0xFFFAFAFA), surface = Color(0xFFFAFAFA), onSurface = Color(0xFF383A42), surfaceContainer = Color(0xFFEAEAEB), onSurfaceVariant = Color(0xFF696C77), outlineVariant = Color(0xFFDBDBDC), shadow = Color(0xFFE5E5E6), hover = Color(0xFF0184BC), onHover = Color(0xFFFAFAFA)),
        ),
        NamedPalette(
            "Monokai Pro",
            dark = NoctaliaColors(primary = Color(0xFFFFD866), onPrimary = Color(0xFF2D2A2E), secondary = Color(0xFFAB9DF2), onSecondary = Color(0xFF2D2A2E), tertiary = Color(0xFFA9DC76), onTertiary = Color(0xFF2D2A2E), error = Color(0xFFFF6188), onError = Color(0xFF2D2A2E), surface = Color(0xFF2D2A2E), onSurface = Color(0xFFFCFCFA), surfaceContainer = Color(0xFF403E41), onSurfaceVariant = Color(0xFF939293), outlineVariant = Color(0xFF5B595C), shadow = Color(0xFF221F22), hover = Color(0xFF78DCE8), onHover = Color(0xFF2D2A2E)),
            light = NoctaliaColors(primary = Color(0xFFCC7A0A), onPrimary = Color(0xFFFAF4F2), secondary = Color(0xFF7058BE), onSecondary = Color(0xFFFAF4F2), tertiary = Color(0xFF269D69), onTertiary = Color(0xFFFAF4F2), error = Color(0xFFE14775), onError = Color(0xFFFAF4F2), surface = Color(0xFFFAF4F2), onSurface = Color(0xFF29242A), surfaceContainer = Color(0xFFEDE7E5), onSurfaceVariant = Color(0xFF706B6E), outlineVariant = Color(0xFFD3CDCC), shadow = Color(0xFFE5DFDD), hover = Color(0xFF1C8CA8), onHover = Color(0xFFFAF4F2)),
        ),
        NamedPalette(
            "Synthwave '84",
            dark = NoctaliaColors(primary = Color(0xFFFF7EDB), onPrimary = Color(0xFF262335), secondary = Color(0xFF36F9F6), onSecondary = Color(0xFF262335), tertiary = Color(0xFFFEDE5D), onTertiary = Color(0xFF262335), error = Color(0xFFFE4450), onError = Color(0xFF262335), surface = Color(0xFF262335), onSurface = Color(0xFFF4EEFF), surfaceContainer = Color(0xFF34294F), onSurfaceVariant = Color(0xFF848BBD), outlineVariant = Color(0xFF495495), shadow = Color(0xFF171520), hover = Color(0xFF72F1B8), onHover = Color(0xFF262335)),
            light = NoctaliaColors(primary = Color(0xFFD61F8C), onPrimary = Color(0xFFFFFFFF), secondary = Color(0xFF0A9396), onSecondary = Color(0xFFFFFFFF), tertiary = Color(0xFFB7791F), onTertiary = Color(0xFFFFFFFF), error = Color(0xFFD7263D), onError = Color(0xFFFFFFFF), surface = Color(0xFFFBF5FF), onSurface = Color(0xFF2A2139), surfaceContainer = Color(0xFFEFE4FA), onSurfaceVariant = Color(0xFF6B5F88), outlineVariant = Color(0xFFD9CCEB), shadow = Color(0xFFE7DCF3), hover = Color(0xFF0A9396), onHover = Color(0xFFFFFFFF)),
        ),
        NamedPalette(
            "Oxocarbon",
            dark = NoctaliaColors(primary = Color(0xFF78A9FF), onPrimary = Color(0xFF161616), secondary = Color(0xFFFF7EB6), onSecondary = Color(0xFF161616), tertiary = Color(0xFF3DDBD9), onTertiary = Color(0xFF161616), error = Color(0xFFFA4D56), onError = Color(0xFF161616), surface = Color(0xFF161616), onSurface = Color(0xFFF2F4F8), surfaceContainer = Color(0xFF262626), onSurfaceVariant = Color(0xFFA2A9B0), outlineVariant = Color(0xFF393939), shadow = Color(0xFF0B0B0B), hover = Color(0xFFBE95FF), onHover = Color(0xFF161616)),
            light = NoctaliaColors(primary = Color(0xFF0F62FE), onPrimary = Color(0xFFFFFFFF), secondary = Color(0xFFEE5396), onSecondary = Color(0xFFFFFFFF), tertiary = Color(0xFF007D79), onTertiary = Color(0xFFFFFFFF), error = Color(0xFFDA1E28), onError = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF161616), surfaceContainer = Color(0xFFF2F4F8), onSurfaceVariant = Color(0xFF525252), outlineVariant = Color(0xFFDDE1E6), shadow = Color(0xFFE8E8E8), hover = Color(0xFF8A3FFC), onHover = Color(0xFFFFFFFF)),
        ),
    ).sortedBy { it.name.lowercase() }

    /** Palettes from the `palettes/` folder; one with a built-in's name replaces it. */
    var user: List<NamedPalette> by mutableStateOf(emptyList())

    /** Built-ins, then the user's, as the theme switcher lists them. */
    val all: List<NamedPalette>
        get() = builtIn.filter { b -> user.none { it.name.equals(b.name, ignoreCase = true) } } + user

    fun byName(name: String): NamedPalette? =
        user.lastOrNull { it.name.equals(name, ignoreCase = true) } ?: builtIn.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
