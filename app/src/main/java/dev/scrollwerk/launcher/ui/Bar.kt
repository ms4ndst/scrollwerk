package dev.scrollwerk.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.widgets.MediaSessions
import dev.scrollwerk.launcher.widgets.NowPlaying
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Noctalia's bar: launcher button · clock · battery + control center, as capsules on a 34 dp strip. */
@Composable
fun Bar(
    config: LauncherConfig.Bar,
    onLauncherClick: () -> Unit,
    onControlCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    workspaceDots: (@Composable () -> Unit)? = null,
    /** False while the notification bar shows the track instead (it has the room for it). */
    showMedia: Boolean = config.media,
) {
    val style = Noctalia.style
    val c = style.colors
    val shape = if (config.floating) RoundedCornerShape(style.radiusXl) else RectangleShape
    // The clock stays centred, so it may only be as wide as the gap the wider side leaves.
    var leftPx by remember { mutableIntStateOf(0) }
    var rightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (config.floating) Modifier.padding(horizontal = 8.dp, vertical = 6.dp) else Modifier)
            .height(NoctaliaStyle.barThickness)
            .clip(shape)
            .background(c.surface)
            .then(if (config.floating) Modifier.border(NoctaliaStyle.borderWidth, c.outlineVariant, shape) else Modifier)
            .padding(horizontal = NoctaliaStyle.barCapsulePadding),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).onSizeChanged { leftPx = it.width },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Capsule(onClick = onLauncherClick) {
                Icon(NIcons.Apps, contentDescription = "Open launcher", tint = c.primary, modifier = Modifier.size(16.dp))
            }
            workspaceDots?.let { Capsule(padding = 6.dp) { it() } }
        }
        val side = with(density) { maxOf(leftPx, rightPx).toDp() }
        val centerMax = (maxWidth - side * 2 - CLOCK_GAP * 2).coerceAtLeast(56.dp)
        Capsule(Modifier.align(Alignment.Center).widthIn(max = centerMax)) { Clock(config, showMedia, centerMax) }
        Row(
            Modifier.align(Alignment.CenterEnd).onSizeChanged { rightPx = it.width },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Capsule { Battery() }
            Capsule(onClick = onControlCenterClick) {
                Icon(NIcons.Sliders, contentDescription = "Control center", tint = c.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Capsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Noctalia.colors
    Row(
        modifier
            .height(NoctaliaStyle.barThickness - NoctaliaStyle.barCapsulePadding * 2)
            .clip(CircleShape)
            .background(c.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Space kept between the clock capsule and its neighbours. */
private val CLOCK_GAP = 4.dp

/** The capsule's horizontal padding (2 × 10 dp) plus the gap between time and date (6 dp). */
private val CLOCK_CHROME = 26.dp

private val DATE_SKELETONS = listOf("EEEdMMM", "dMMM", "EEEd")

@Composable
private fun RowScope.Clock(config: LauncherConfig.Bar, showMedia: Boolean, maxWidth: Dp) {
    val c = Noctalia.colors
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50) // wake just after each minute boundary
        }
    }
    val is24 = config.use24Hour ?: android.text.format.DateFormat.is24HourFormat(context)
    val time = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a") }
    // Longest first: "Wed 11 Nov", "11 Nov", "Wed 11", in the phone's own order and language.
    val dates = remember {
        DATE_SKELETONS.map { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), it)) }
    }
    val instant = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val timeText = time.format(instant)
    val timeStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val dateStyle = TextStyle(fontSize = 13.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The longest date that fits beside the time without crowding the bar's sides.
    val dateText = remember(timeText, maxWidth, density) {
        val room = with(density) { (maxWidth - CLOCK_CHROME).toPx() } - measurer.measure(timeText, timeStyle).size.width
        val texts = dates.map { it.format(instant) }
        texts.firstOrNull { measurer.measure(it, dateStyle).size.width <= room } ?: texts.last()
    }
    Text(timeText, color = c.onSurface, style = timeStyle, maxLines = 1)
    val media = if (showMedia) rememberBarMedia() else null
    when {
        media != null -> NowPlayingChip(media, Modifier.weight(1f, fill = false))
        config.showDate -> Text(dateText, color = c.onSurfaceVariant, style = dateStyle, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** How long a paused track stays in the bar before the date comes back. */
private const val PAUSED_LINGER_MS = 120_000L

/** What's playing, for the bar: null when nothing is, or when it's been paused for a while. */
@Composable
internal fun rememberBarMedia(): NowPlaying? {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(MediaSessions.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = MediaSessions.hasAccess(context) }
    if (!hasAccess) return null
    val flow = remember(hasAccess) { MediaSessions.nowPlaying(context) }
    val media by flow.collectAsStateWithLifecycle(null)
    var pausedLongEnough by remember { mutableStateOf(false) }
    val playing = media?.playing == true
    LaunchedEffect(playing, media?.title) {
        pausedLongEnough = false
        if (!playing) { delay(PAUSED_LINGER_MS); pausedLongEnough = true }
    }
    return media?.takeUnless { pausedLongEnough }
}

/** ♪ Title, scrolling if long. Tap plays / pauses; long-press opens the player. */
@Composable
internal fun NowPlayingChip(media: NowPlaying, modifier: Modifier = Modifier, withArtist: Boolean = false) {
    val c = Noctalia.colors
    val context = LocalContext.current
    val controls = media.controller.transportControls
    Row(
        modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = { if (media.playing) controls.pause() else controls.play() },
                onLongClick = {
                    runCatching { media.controller.sessionActivity?.send() }.onFailure {
                        context.packageManager.getLaunchIntentForPackage(media.controller.packageName)
                            ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (media.playing) NIcons.Music else NIcons.Play,
            contentDescription = if (media.playing) "Pause ${media.title}" else "Play ${media.title}",
            tint = c.tertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            if (withArtist && media.artist != null) "${media.title} — ${media.artist}" else media.title,
            color = if (media.playing) c.onSurfaceVariant else c.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 13.sp,
            maxLines = 1,
            // Shrinks to whatever room the bar leaves, scrolling when the title doesn't fit.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 160.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
        )
    }
}

private data class BatteryState(val percent: Int, val charging: Boolean)

@Composable
private fun Battery() {
    val c = Noctalia.colors
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    state = BatteryState(
                        level * 100 / scale,
                        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    )
                }
            }
        }
        // Sticky broadcast: the current value is delivered immediately.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val s = state ?: return
    val fill = when {
        s.charging -> c.tertiary
        s.percent <= 15 -> c.error
        else -> c.onSurface
    }
    if (s.charging) Icon(NIcons.Bolt, contentDescription = "Charging", tint = c.tertiary, modifier = Modifier.size(13.dp))
    BatteryGlyph(s.percent, fill, outline = c.onSurfaceVariant)
    Text("${s.percent}%", color = c.onSurface, fontSize = 13.sp)
}

@Composable
private fun BatteryGlyph(percent: Int, fill: Color, outline: Color) {
    Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
        val stroke = 1.2.dp.toPx()
        val tip = 2.dp.toPx()
        val body = Size(size.width - tip - stroke, size.height - stroke)
        val r = CornerRadius(2.5.dp.toPx())
        drawRoundRect(outline, Offset(stroke / 2, stroke / 2), body, r, style = Stroke(stroke))
        val inset = stroke + 1.dp.toPx()
        val inner = Size((body.width - inset * 2 + stroke) * percent.coerceIn(0, 100) / 100f, body.height - inset * 2 + stroke)
        drawRoundRect(fill, Offset(inset, inset), inner, CornerRadius(1.dp.toPx()))
        drawRoundRect(outline, Offset(size.width - tip, size.height * 0.3f), Size(tip, size.height * 0.4f), CornerRadius(1.dp.toPx()))
    }
}
