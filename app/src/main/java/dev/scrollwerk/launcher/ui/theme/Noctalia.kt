package dev.scrollwerk.launcher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.config.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Noctalia's 16 palette roles (format 2); the remaining roles are derived the same way
 * Noctalia derives them. Values for [Default] come from Noctalia's built-in "Noctalia"
 * palette (dark variant).
 */
@Immutable
data class NoctaliaColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceContainer: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color,
    val shadow: Color,
    val hover: Color,
    val onHover: Color,
) {
    val surfaceContainerLow get() = lerp(surface, surfaceContainer, 0.5f)
    val surfaceContainerHigh get() = lerp(surfaceContainer, onSurface, 0.06f)
    val primaryContainer get() = lerp(surface, primary, 0.25f)
    val scrim get() = Color.Black

    companion object {
        val Default = NoctaliaColors(
            primary = Color(0xFFFFF59B),
            onPrimary = Color(0xFF0E0E43),
            secondary = Color(0xFFA9AEFE),
            onSecondary = Color(0xFF0E0E43),
            tertiary = Color(0xFF9BFECE),
            onTertiary = Color(0xFF0E0E43),
            error = Color(0xFFFD4663),
            onError = Color(0xFF0E0E43),
            surface = Color(0xFF070722),
            onSurface = Color(0xFFF3EDF7),
            surfaceContainer = Color(0xFF11112D),
            onSurfaceVariant = Color(0xFF7C80B4),
            outlineVariant = Color(0xFF21215F),
            shadow = Color(0xFF070722),
            hover = Color(0xFF9BFECE),
            onHover = Color(0xFF0E0E43),
        )

        /** Noctalia's `source = "wallpaper"`: roles taken from the Material scheme. */
        fun fromMaterial(s: ColorScheme) = NoctaliaColors(
            primary = s.primary, onPrimary = s.onPrimary,
            secondary = s.secondary, onSecondary = s.onSecondary,
            tertiary = s.tertiary, onTertiary = s.onTertiary,
            error = s.error, onError = s.onError,
            surface = s.surface, onSurface = s.onSurface,
            surfaceContainer = s.surfaceContainer, onSurfaceVariant = s.onSurfaceVariant,
            outlineVariant = s.outlineVariant, shadow = Color.Black,
            hover = s.tertiary, onHover = s.onTertiary,
        )
    }
}

/** Noctalia's Style constants (lib/ui/style.h), in dp. */
@Immutable
data class NoctaliaStyle(
    val colors: NoctaliaColors,
    val radiusScale: Float,
    val isDark: Boolean = true,
    /** Theme touches such as Kanagawa's kanji (see [ThemeExtras]). */
    val extras: ThemeExtras = ThemeExtras.NONE,
) {
    val radiusSm: Dp get() = (3 * radiusScale).dp
    val radiusMd: Dp get() = (6 * radiusScale).dp
    val radiusLg: Dp get() = (9 * radiusScale).dp
    val radiusXl: Dp get() = (12 * radiusScale).dp

    companion object {
        val barThickness = 34.dp
        val barCapsulePadding = 6.dp
        val panelPadding = 14.dp
        val spaceXs = 4.dp
        val spaceSm = 8.dp
        val spaceMd = 12.dp
        val spaceLg = 16.dp
        val borderWidth = 1.dp
        val controlHeightLg = 44.dp
        const val ANIM_FAST = 100
        const val ANIM_NORMAL = 200
    }
}

private val LocalNoctalia = staticCompositionLocalOf { NoctaliaStyle(NoctaliaColors.Default, 1f) }

object Noctalia {
    val style: NoctaliaStyle @Composable get() = LocalNoctalia.current
    val colors: NoctaliaColors @Composable get() = LocalNoctalia.current.colors
}

/** Resolves the configured palette name + mode to colours. "wallpaper" uses Material You. */
@Composable
fun resolveColors(theme: LauncherConfig.Theme): Pair<NoctaliaColors, Boolean> {
    val dark = when (theme.mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = if (theme.palette.equals(WALLPAPER, ignoreCase = true)) {
        remember(context, dark) {
            NoctaliaColors.fromMaterial(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        }
    } else {
        val palette = Palettes.byName(theme.palette) ?: Palettes.byName("Noctalia")!!
        if (dark) palette.dark else palette.light
    }
    return colors to dark
}

const val WALLPAPER = "wallpaper"

/** Material's type scale in the theme's font (Text everywhere inherits it), or the phone's font. */
private fun typographyFor(theme: LauncherConfig.Theme): Typography {
    val palette = if (theme.palette.equals(WALLPAPER, ignoreCase = true)) null
    else Palettes.byName(theme.palette) ?: Palettes.byName("Noctalia")
    val family = ThemeFonts.family(ThemeFonts.resolve(theme.font, theme.headingFont, palette)) ?: return Typography()
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun NoctaliaTheme(theme: LauncherConfig.Theme, content: @Composable () -> Unit) {
    val (colors, dark) = resolveColors(theme)
    val style = NoctaliaStyle(colors, theme.cornerRadiusScale.coerceIn(0f, 4f), dark, ThemeExtras.forPalette(theme.palette))
    // Material components (menus, text selection) pick up the same palette.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.primary, onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onSurface,
        secondary = colors.secondary, onSecondary = colors.onSecondary,
        tertiary = colors.tertiary, onTertiary = colors.onTertiary,
        error = colors.error, onError = colors.onError,
        background = colors.surface, onBackground = colors.onSurface,
        surface = colors.surface, onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceContainer, onSurfaceVariant = colors.onSurfaceVariant,
        surfaceContainerLowest = colors.surface, surfaceContainerLow = colors.surfaceContainerLow,
        surfaceContainer = colors.surfaceContainer, surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHigh,
        outline = colors.outlineVariant, outlineVariant = colors.outlineVariant,
        scrim = colors.scrim,
    )
    CompositionLocalProvider(LocalNoctalia provides style) {
        MaterialTheme(colorScheme = scheme, typography = typographyFor(theme), content = content)
    }
}
