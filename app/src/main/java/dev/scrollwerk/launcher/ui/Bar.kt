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
) {
    val style = Noctalia.style
    val c = style.colors
    val shape = if (config.floating) RoundedCornerShape(style.radiusXl) else RectangleShape
    Box(
        modifier
            .fillMaxWidth()
            .then(if (config.floating) Modifier.padding(horizontal = 8.dp, vertical = 6.dp) else Modifier)
            .height(NoctaliaStyle.barThickness)
            .clip(shape)
            .background(c.surface)
            .then(if (config.floating) Modifier.border(NoctaliaStyle.borderWidth, c.outlineVariant, shape) else Modifier)
            .padding(horizontal = NoctaliaStyle.barCapsulePadding),
    ) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Capsule(onClick = onLauncherClick) {
                Icon(NIcons.Apps, contentDescription = "Open launcher", tint = c.primary, modifier = Modifier.size(16.dp))
            }
            workspaceDots?.let { Capsule(padding = 6.dp) { it() } }
        }
        Capsule(Modifier.align(Alignment.Center)) { Clock(config) }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Capsule(onClick = onControlCenterClick) { Battery() }
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

@Composable
private fun Clock(config: LauncherConfig.Bar) {
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
    val date = remember {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"))
    }
    val instant = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Text(time.format(instant), color = c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    if (config.showDate) Text(date.format(instant), color = c.onSurfaceVariant, fontSize = 13.sp)
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
