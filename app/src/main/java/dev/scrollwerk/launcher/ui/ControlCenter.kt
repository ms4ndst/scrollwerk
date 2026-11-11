package dev.scrollwerk.launcher.ui

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.scrollwerk.launcher.ui.theme.WallpaperGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.config.ThemeMode
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaColors
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import dev.scrollwerk.launcher.ui.theme.Palettes
import dev.scrollwerk.launcher.ui.theme.WALLPAPER
import dev.scrollwerk.launcher.widgets.Flashlight
import dev.scrollwerk.launcher.notifications.EmailApps
import dev.scrollwerk.launcher.notifications.NotificationFeed
import dev.scrollwerk.launcher.notifications.Source
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/** Noctalia's Control Center, phone-sized: quick tiles and the theme switcher. */
@Composable
fun ControlCenter(
    visible: Boolean,
    theme: LauncherConfig.Theme,
    onThemeChange: (LauncherConfig.Theme) -> Unit,
    onApplyWallpaper: () -> Unit,
    widgets: LauncherConfig.Widgets,
    onWidgetsChange: (LauncherConfig.Widgets) -> Unit,
    notifications: LauncherConfig.Notifications,
    onNotificationsChange: (LauncherConfig.Notifications) -> Unit,
    onPickOtherEmailApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val style = Noctalia.style
    val c = style.colors

    AnimatedVisibility(visible, enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)), exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST))) {
        Box(
            Modifier.fillMaxSize().background(c.scrim.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp)
            .padding(top = NoctaliaStyle.barThickness + 18.dp, bottom = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)) + slideInVertically(tween(NoctaliaStyle.ANIM_NORMAL)) { -it / 10 },
            exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST)) + slideOutVertically(tween(NoctaliaStyle.ANIM_FAST)) { -it / 20 },
        ) {
            val shape = RoundedCornerShape(style.radiusXl)
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .clip(shape)
                    .background(c.surface)
                    .border(NoctaliaStyle.borderWidth, c.outlineVariant, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(NoctaliaStyle.panelPadding),
                verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceMd),
            ) {
                SectionTitle(NIcons.Sliders, "Quick settings")
                QuickTiles()
                Spacer(Modifier.height(2.dp))
                SectionTitle(NIcons.Palette, "Theme")
                ModeSelector(theme.mode) { onThemeChange(theme.copy(mode = it)) }
                PaletteGrid(theme) { onThemeChange(theme.copy(palette = it)) }
                WallpaperOptions(
                    theme = theme,
                    onToggle = { onThemeChange(theme.copy(matchingWallpaper = it)) },
                    onApply = onApplyWallpaper,
                )
                ThemedIconsOption(theme.themedIcons) { onThemeChange(theme.copy(themedIcons = it)) }
                Spacer(Modifier.height(2.dp))
                SectionTitle(NIcons.Apps, "Widgets")
                WidgetToggles(widgets, onWidgetsChange)
                Spacer(Modifier.height(2.dp))
                SectionTitle(NIcons.Bell, "Notifications")
                NotificationSettings(notifications, onNotificationsChange, onPickOtherEmailApp)
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String) {
    val c = Noctalia.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(16.dp))
        Text(text, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- quick tiles

@Composable
private fun QuickTiles() {
    val context = LocalContext.current
    val flashlight = remember { Flashlight(context) }
    val torchOn by remember { flashlight.state() }.collectAsStateWithLifecycle(false)
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    val tiles = buildList {
        if (flashlight.available) add(Tile(NIcons.Flashlight, "Flashlight", active = torchOn) { flashlight.set(!torchOn) })
        add(Tile(NIcons.Wifi, "Wi-Fi") { open(Intent(Settings.Panel.ACTION_WIFI)) })
        add(Tile(NIcons.Bluetooth, "Bluetooth") { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        add(Tile(NIcons.Volume, "Volume") { open(Intent(Settings.Panel.ACTION_VOLUME)) })
        add(Tile(NIcons.Photo, "Wallpaper") { open(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")) })
        add(Tile(NIcons.Settings, "Settings") { open(Intent(Settings.ACTION_SETTINGS)) })
    }
    tiles.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm)) {
            row.forEach { TileView(it, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private class Tile(val icon: ImageVector, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@Composable
private fun TileView(tile: Tile, modifier: Modifier) {
    val style = Noctalia.style
    val c = style.colors
    Column(
        modifier
            .clip(RoundedCornerShape(style.radiusLg))
            .background(if (tile.active) c.primary else c.surfaceContainer)
            .clickable(onClick = tile.onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(tile.icon, null, tint = if (tile.active) c.onPrimary else c.primary, modifier = Modifier.size(22.dp))
        Text(tile.label, color = if (tile.active) c.onPrimary else c.onSurface, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- theme switcher

@Composable
private fun ModeSelector(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(c.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            Triple(ThemeMode.Dark, NIcons.Moon, "Dark"),
            Triple(ThemeMode.Light, NIcons.Sun, "Light"),
            Triple(ThemeMode.System, NIcons.Settings, "System"),
        ).forEach { (m, icon, label) ->
            val active = m == mode
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) c.primary else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (active) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (active) c.onPrimary else c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PaletteGrid(theme: LauncherConfig.Theme, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val dark = Noctalia.style.isDark
    val wallpaper = remember(context, dark) {
        NoctaliaColors.fromMaterial(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
    }
    val options = Palettes.all.map { it.name to if (dark) it.dark else it.light } + ("Wallpaper" to wallpaper)
    // Small renders of each palette's matching wallpaper, made off the main thread.
    val previews by produceState(emptyMap<String, ImageBitmap>(), dark) {
        value = withContext(Dispatchers.Default) {
            Palettes.all.associate { p ->
                p.name to WallpaperGenerator.render(if (dark) p.dark else p.light, 108, 234, WallpaperGenerator.seedFor(p.name)).asImageBitmap()
            }
        }
    }
    options.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm)) {
            row.forEach { (name, colors) ->
                val key = if (name == "Wallpaper") WALLPAPER else name
                PaletteCard(
                    name, colors, previews[name],
                    selected = theme.palette.equals(key, ignoreCase = true),
                    modifier = Modifier.weight(1f),
                ) { onSelect(key) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** A palette drawn in its own colours, with a mini phone showing its matching wallpaper. */
@Composable
private fun PaletteCard(
    name: String,
    p: NoctaliaColors,
    preview: ImageBitmap?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val style = Noctalia.style
    val shape = RoundedCornerShape(style.radiusLg)
    Row(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) style.colors.primary else p.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 30.dp, height = 62.dp)
                .clip(RoundedCornerShape(style.radiusMd))
                .background(p.surfaceContainer)
                .border(1.dp, p.outlineVariant, RoundedCornerShape(style.radiusMd)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) Image(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(NIcons.Photo, null, tint = p.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = p.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(p.primary))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(p.primary, p.secondary, p.tertiary, p.error).forEach {
                    Box(Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(style.radiusSm)).background(it))
                }
            }
        }
    }
}

@Composable
private fun WallpaperOptions(theme: LauncherConfig.Theme, onToggle: (Boolean) -> Unit, onApply: () -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    val fromWallpaper = theme.palette.equals(WALLPAPER, ignoreCase = true)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.radiusLg))
            .background(c.surfaceContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (fromWallpaper) {
            Text("This theme takes its colours from your wallpaper, so there's no wallpaper to match.",
                color = c.onSurfaceVariant, fontSize = 13.sp)
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(style.radiusMd)).clickable { onToggle(!theme.matchingWallpaper) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Matching wallpaper", color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Set it automatically when switching theme. Replaces your current home-screen wallpaper.",
                    color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(theme.matchingWallpaper)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Set ${theme.palette} wallpaper now", color = c.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            PillButton("Apply", onApply)
        }
    }
}

@Composable
private fun ThemedIconsOption(on: Boolean, onToggle: (Boolean) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.radiusLg))
            .background(c.surfaceContainer)
            .clickable { onToggle(!on) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Themed icons", color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("App icons in the theme's colours.",
                color = c.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Toggle(on)
    }
}

/** One switch per desktop widget; turning one off removes its card from the desktop page. */
@Composable
private fun WidgetToggles(widgets: LauncherConfig.Widgets, onChange: (LauncherConfig.Widgets) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    val rows = listOf(
        Triple(NIcons.Sun, "Clock", widgets.clock) to { on: Boolean -> widgets.copy(clock = on) },
        Triple(NIcons.Cloud, "Weather", widgets.weather) to { on: Boolean -> widgets.copy(weather = on) },
        Triple(NIcons.Music, "Media", widgets.media) to { on: Boolean -> widgets.copy(media = on) },
        Triple(NIcons.Calendar, "Agenda", widgets.agenda) to { on: Boolean -> widgets.copy(agenda = on) },
        Triple(NIcons.Chip, "System", widgets.system) to { on: Boolean -> widgets.copy(system = on) },
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(style.radiusLg)).background(c.surfaceContainer).padding(vertical = 4.dp),
    ) {
        rows.forEach { (info, set) ->
            val (icon, label, on) = info
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChange(set(!on)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (on) c.primary else c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, color = c.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Toggle(on)
            }
        }
    }
}

/** The notification bar: on/off, which sources it shows, and which email client counts as "Email". */
@Composable
private fun NotificationSettings(
    config: LauncherConfig.Notifications,
    onChange: (LauncherConfig.Notifications) -> Unit,
    onPickOtherEmailApp: () -> Unit,
) {
    val style = Noctalia.style
    val c = style.colors
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(NotificationFeed.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = NotificationFeed.hasAccess(context) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(style.radiusLg)).background(c.surfaceContainer).padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onChange(config.copy(enabled = !config.enabled)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notification bar", color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Slack, SMS and email alerts under the top bar", color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(config.enabled)
        }
        if (!config.enabled) return@Column
        if (!hasAccess) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SetupPrompt("Needs notification access", "Allow") {
                    runCatching { context.startActivity(NotificationFeed.accessSettingsIntent(context)) }
                }
            }
        }
        listOf(
            Triple(Source.Slack, config.slack) { on: Boolean -> config.copy(slack = on) },
            Triple(Source.Sms, config.sms) { on: Boolean -> config.copy(sms = on) },
            Triple(Source.Email, config.email) { on: Boolean -> config.copy(email = on) },
        ).forEach { (source, on, set) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChange(set(!on)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(source.icon, null, tint = if (on) source.color(c) else c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(source.label, color = c.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Toggle(on)
            }
        }
        if (config.email) {
            Row(
                Modifier.fillMaxWidth().padding(start = 42.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Email client", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                AppChooserChip(
                    selected = config.emailApp,
                    heading = "Email alerts from",
                    defaultOption = "Any email app",
                    nullLabel = "Any email app",
                    installed = EmailApps::installed,
                    onChange = { onChange(config.copy(emailApp = it)) },
                    onPickOther = onPickOtherEmailApp,
                )
            }
        }
    }
}

/** Noctalia's medium toggle: 18 dp thumb, 3 dp inset, 16 dp travel. */
@Composable
internal fun Toggle(checked: Boolean) {
    val c = Noctalia.colors
    val offset by animateDpAsState(if (checked) 16.dp else 0.dp, tween(NoctaliaStyle.ANIM_NORMAL), label = "toggle")
    Box(
        Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) c.primary else c.surfaceContainerHigh)
            .border(1.dp, if (checked) c.primary else c.outlineVariant, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.onSurfaceVariant),
        )
    }
}
