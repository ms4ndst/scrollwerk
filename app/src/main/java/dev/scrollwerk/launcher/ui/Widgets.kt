package dev.scrollwerk.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.widgets.Agenda
import dev.scrollwerk.launcher.widgets.AgendaEvent
import dev.scrollwerk.launcher.widgets.AppChoice
import dev.scrollwerk.launcher.widgets.appLabel
import dev.scrollwerk.launcher.widgets.MediaSessions
import dev.scrollwerk.launcher.widgets.NowPlaying
import dev.scrollwerk.launcher.widgets.Sky
import dev.scrollwerk.launcher.widgets.SystemMonitor
import dev.scrollwerk.launcher.widgets.WeatherCodes
import dev.scrollwerk.launcher.widgets.WeatherRepository
import dev.scrollwerk.launcher.widgets.WeatherState
import dev.scrollwerk.launcher.widgets.formatTemp
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The column of Noctalia-style desktop widgets shown on the home screen. */
@Composable
fun WidgetColumn(
    config: LauncherConfig.Widgets,
    use24Hour: Boolean?,
    weather: WeatherRepository,
    onCalendarAppChange: (String?) -> Unit,
    onPickOtherCalendarApp: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (config.clock) ClockWidget(use24Hour)
        if (config.weather) WeatherWidget(config, weather)
        if (config.media) MediaWidget()
        if (config.agenda) AgendaWidget(use24Hour, config.calendarApp, onCalendarAppChange, onPickOtherCalendarApp)
        if (config.system) SystemWidget()
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun WidgetCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = Noctalia.style
    val shape = RoundedCornerShape(style.radiusXl)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.colors.surface.copy(alpha = 0.94f))
            .border(NoctaliaStyle.borderWidth, style.colors.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(NoctaliaStyle.panelPadding),
        verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
        content = content,
    )
}

@Composable
private fun CardTitle(icon: ImageVector, text: String) {
    val c = Noctalia.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(15.dp))
        Text(text, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Chip showing a chosen app ("Etar ▾"). The menu lists [installed] apps, a null option
 * ([defaultOption], e.g. the system default), and "Other app…" for apps that don't register
 * themselves for the role (picked with the launcher).
 */
@Composable
internal fun AppChooserChip(
    selected: String?,
    heading: String,
    defaultOption: String,
    nullLabel: String,
    installed: (android.content.Context) -> List<AppChoice>,
    onChange: (String?) -> Unit,
    onPickOther: () -> Unit,
) {
    val style = Noctalia.style
    val c = style.colors
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val label = remember(selected, nullLabel) { selected?.let { appLabel(context, it) ?: it } ?: nullLabel }
    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.surfaceContainer)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = c.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp))
            Text(" ▾", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = c.surfaceContainer,
            shape = RoundedCornerShape(style.radiusLg),
        ) {
            val apps = remember(open) { if (open) installed(context) else emptyList() }
            @Composable
            fun option(text: String, isSelected: Boolean, action: () -> Unit) = DropdownMenuItem(
                text = { Text(text, color = if (isSelected) c.primary else c.onSurface, fontWeight = if (isSelected) FontWeight.SemiBold else null) },
                onClick = { open = false; action() },
            )
            Text(heading, color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            option(defaultOption, selected == null) { onChange(null) }
            apps.forEach { app -> option(app.label, app.packageName == selected) { onChange(app.packageName) } }
            if (selected != null && apps.none { it.packageName == selected }) option(label, true) {}
            option("Other app…", false, onPickOther)
        }
    }
}

/** The agenda's header chip: which app events open in. */
@Composable
private fun CalendarAppChooser(calendarApp: String?, onChange: (String?) -> Unit, onPickOther: () -> Unit) =
    AppChooserChip(
        selected = calendarApp,
        heading = "Open events in",
        defaultOption = "System default",
        nullLabel = "Default app",
        installed = Agenda::calendarApps,
        onChange = onChange,
        onPickOther = onPickOther,
    )

/** Explains why a widget is empty and offers the one action that fixes it. */
@Composable
internal fun SetupPrompt(text: String, action: String, onAction: () -> Unit) {
    val c = Noctalia.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(NoctaliaStyle.spaceSm))
        PillButton(action, onAction)
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    val c = Noctalia.colors
    Text(
        text,
        color = c.onPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(c.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Asks for [permission]; once Android has refused (e.g. "Don't allow" twice), the same
 * button opens this app's settings page instead, where the user can still grant it.
 */
@Composable
private fun rememberPermissionRequest(permission: String, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        onResult(ok)
    }
    return {
        if (refused) runCatching { context.startActivity(appSettingsIntent(context.packageName)) }
        else launcher.launch(permission)
    }
}

@Composable
private fun rememberTicker(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs + 50)
        }
    }
    return now
}

@Composable
private fun is24(use24Hour: Boolean?): Boolean =
    use24Hour ?: android.text.format.DateFormat.is24HourFormat(LocalContext.current)

// ---------------------------------------------------------------- clock

@Composable
private fun ClockWidget(use24Hour: Boolean?) {
    val c = Noctalia.colors
    val now = rememberTicker(60_000)
    val h24 = is24(use24Hour)
    val time = remember(h24) { DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm") }
    val date = remember {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"))
    }
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    // The clock sits directly on the wallpaper, so its contrast follows the wallpaper, not the theme.
    val context = LocalContext.current
    var darkText by remember { mutableStateOf(wallpaperWantsDarkText(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { darkText = wallpaperWantsDarkText(context) }
    val ink = if (darkText) Color(0xFF111111) else Color.White
    val shadow = Shadow(if (darkText) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 12f)
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time.format(t), color = ink, fontSize = 72.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp,
            style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = shadow),
        )
        Text(
            date.format(t), color = c.primary, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = shadow),
        )
    }
}

private fun wallpaperWantsDarkText(context: android.content.Context): Boolean = runCatching {
    val colors = android.app.WallpaperManager.getInstance(context).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
    (colors?.colorHints ?: 0) and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
}.getOrDefault(false)

// ---------------------------------------------------------------- weather

private fun Sky.icon(isDay: Boolean): ImageVector = when (this) {
    Sky.Clear -> if (isDay) NIcons.Sun else NIcons.Moon
    Sky.PartlyCloudy -> if (isDay) NIcons.CloudSun else NIcons.Cloud
    Sky.Cloudy -> NIcons.Cloud
    Sky.Fog -> NIcons.Fog
    Sky.Drizzle, Sky.Rain -> NIcons.Rain
    Sky.Snow -> NIcons.Snow
    Sky.Storm -> NIcons.Storm
}

@Composable
private fun WeatherWidget(config: LauncherConfig.Widgets, repository: WeatherRepository) {
    val c = Noctalia.colors
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    var resumes by remember { mutableLongStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = granted(); resumes++ }
    val requestLocation = rememberPermissionRequest(Manifest.permission.ACCESS_COARSE_LOCATION) { hasPermission = it }

    var state by remember { mutableStateOf<WeatherState>(WeatherState.Loading) }
    LaunchedEffect(config.weatherLocation, config.temperatureUnit, hasPermission, resumes) {
        while (true) {
            state = repository.load(config.weatherLocation, config.temperatureUnit, hasPermission)
            delay(30 * 60_000L)
        }
    }

    WidgetCard {
        when (val s = state) {
            WeatherState.Loading -> {
                CardTitle(NIcons.Cloud, "Weather")
                Text("Loading…", color = c.onSurfaceVariant, fontSize = 14.sp)
            }
            WeatherState.NeedsLocation -> {
                CardTitle(NIcons.Cloud, "Weather")
                SetupPrompt("Weather for where you are", "Use location", requestLocation)
                Text("Or set \"weatherLocation\" in config.json", color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            is WeatherState.Failed -> {
                CardTitle(NIcons.Cloud, "Weather")
                Text(s.message, color = c.onSurfaceVariant, fontSize = 14.sp)
            }
            is WeatherState.Ready -> {
                val w = s.weather
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        WeatherCodes.sky(w.code).icon(w.isDay), null,
                        tint = c.primary, modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(NoctaliaStyle.spaceMd))
                    Column(Modifier.weight(1f)) {
                        Text(formatTemp(w.temperature), color = c.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
                        Text(WeatherCodes.describe(w.code), color = c.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(NIcons.MapPin, null, tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            Text(w.place, color = c.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        w.days.firstOrNull()?.let {
                            Text("${formatTemp(it.high)} / ${formatTemp(it.low)}", color = c.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    w.days.drop(1).take(5).forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(dayName(day.date), color = c.onSurfaceVariant, fontSize = 12.sp)
                            Icon(WeatherCodes.sky(day.code).icon(true), null, tint = c.secondary, modifier = Modifier.size(20.dp))
                            Text(formatTemp(day.high), color = c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(formatTemp(day.low), color = c.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

// ---------------------------------------------------------------- media

@Composable
private fun MediaWidget() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(MediaSessions.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = MediaSessions.hasAccess(context) }

    if (!hasAccess) {
        WidgetCard {
            CardTitle(NIcons.Music, "Media")
            SetupPrompt("Show what's playing, with controls", "Enable") {
                runCatching { context.startActivity(MediaSessions.accessSettingsIntent(context)) }
            }
        }
        return
    }
    val flow = remember(hasAccess) { MediaSessions.nowPlaying(context) }
    val playing by flow.collectAsStateWithLifecycle(null)
    playing?.let { MediaCard(it) } // nothing playing → no card, like Noctalia
}

@Composable
private fun MediaCard(media: NowPlaying) {
    val style = Noctalia.style
    val c = style.colors
    val context = LocalContext.current
    val controls = media.controller.transportControls
    WidgetCard(onClick = {
        runCatching { media.controller.sessionActivity?.send() }.onFailure {
            context.packageManager.getLaunchIntentForPackage(media.controller.packageName)?.let(context::startActivity)
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(style.radiusLg)).background(c.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                val art = remember(media.art) { media.art?.asImageBitmap() }
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(NIcons.Music, null, tint = c.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(NoctaliaStyle.spaceMd))
            Column(Modifier.weight(1f)) {
                Text(media.title, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                media.artist?.let { Text(it, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            MediaButton(NIcons.Prev, "Previous", filled = false) { controls.skipToPrevious() }
            MediaButton(if (media.playing) NIcons.Pause else NIcons.Play, if (media.playing) "Pause" else "Play", filled = true) {
                if (media.playing) controls.pause() else controls.play()
            }
            MediaButton(NIcons.Next, "Next", filled = false) { controls.skipToNext() }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val c = Noctalia.colors
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (filled) c.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (filled) c.onPrimary else c.onSurface, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- agenda

@Composable
private fun AgendaWidget(
    use24Hour: Boolean?,
    calendarApp: String?,
    onCalendarAppChange: (String?) -> Unit,
    onPickOther: () -> Unit,
) {
    val c = Noctalia.colors
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(Agenda.hasPermission(context)) }
    var resumes by remember { mutableLongStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = Agenda.hasPermission(context); resumes++ }
    val requestCalendar = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { hasPermission = it }

    var events by remember { mutableStateOf<List<AgendaEvent>?>(null) }
    LaunchedEffect(hasPermission, resumes) {
        while (hasPermission) {
            events = withContext(Dispatchers.IO) { Agenda.upcoming(context) }
            delay(5 * 60_000L)
        }
    }

    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Noctalia.style.radiusMd))
                    .clickable { Agenda.openApp(context, calendarApp) },
            ) { CardTitle(NIcons.Calendar, "Agenda") }
            CalendarAppChooser(calendarApp, onCalendarAppChange, onPickOther)
        }
        when {
            !hasPermission -> SetupPrompt("See your next events", "Allow", requestCalendar)
            events == null -> Text("Loading…", color = c.onSurfaceVariant, fontSize = 14.sp)
            events!!.isEmpty() -> Text("Nothing in the next two days", color = c.onSurfaceVariant, fontSize = 14.sp)
            else -> {
                val h24 = is24(use24Hour)
                events!!.forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Noctalia.style.radiusMd))
                            .clickable { Agenda.openEvent(context, e, calendarApp) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(3.dp).height(30.dp).clip(CircleShape)
                                .background(e.color?.let { Color(it) } ?: c.secondary),
                        )
                        Spacer(Modifier.width(NoctaliaStyle.spaceSm))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = c.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(whenText(e, h24), color = c.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun whenText(e: AgendaEvent, h24: Boolean): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // All-day events are stored in UTC.
    val start = Instant.ofEpochMilli(e.begin).atZone(if (e.allDay) ZoneId.of("UTC") else zone)
    val day = when (start.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    if (e.allDay) return "$day · All day"
    val fmt = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a")
    val end = Instant.ofEpochMilli(e.end).atZone(zone)
    return if (e.begin <= System.currentTimeMillis()) "Now · until ${fmt.format(end)}"
    else "$day · ${fmt.format(start)}–${fmt.format(end)}"
}

// ---------------------------------------------------------------- system monitor

@Composable
private fun SystemWidget() {
    val c = Noctalia.colors
    val context = LocalContext.current
    val flow = remember { SystemMonitor.stats(context) }
    val stats by flow.collectAsStateWithLifecycle(null)
    val s = stats ?: return
    WidgetCard {
        CardTitle(NIcons.Chip, "System")
        Meter("Memory", "${gb(s.ramUsed)} / ${gb(s.ramTotal)}", s.ramFraction, c.primary)
        Meter("Storage", "${gb(s.storageUsed)} / ${gb(s.storageTotal)}", s.storageFraction, c.secondary)
        s.batteryTempC?.let { t ->
            Meter("Battery temp", "%.1f°C".format(t), ((t - 20f) / 25f).coerceIn(0f, 1f), if (t > 40) c.error else c.tertiary)
        }
    }
}

private fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_073_741_824.0)

/** Noctalia slider-style meter: 8 dp track, rounded fill. */
@Composable
private fun Meter(label: String, value: String, fraction: Float, color: Color) {
    val c = Noctalia.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, color = c.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = c.onSurfaceVariant, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.surfaceContainer)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

/** Opens this app's system settings page (e.g. after a permission was permanently denied). */
fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
