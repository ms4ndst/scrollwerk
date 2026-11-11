package dev.scrollwerk.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.notifications.Alert
import dev.scrollwerk.launcher.notifications.Badge
import dev.scrollwerk.launcher.notifications.NotificationFeed
import dev.scrollwerk.launcher.notifications.Source
import dev.scrollwerk.launcher.notifications.ageText
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaColors
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import kotlinx.coroutines.delay

internal val Source.icon: ImageVector
    get() = when (this) {
        Source.Slack -> NIcons.Hash
        Source.Sms -> NIcons.Message
        Source.Email -> NIcons.Mail
    }

internal fun Source.color(c: NoctaliaColors): Color = when (this) {
    Source.Slack -> c.secondary
    Source.Sms -> c.tertiary
    Source.Email -> c.primary
}

internal fun Source.onColor(c: NoctaliaColors): Color = when (this) {
    Source.Slack -> c.onSecondary
    Source.Sms -> c.onTertiary
    Source.Email -> c.onPrimary
}

/** The alert count on a workspace column, in its source's colour. */
@Composable
internal fun CountBadge(badge: Badge, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = Noctalia.colors
    Text(
        if (badge.count > 99) "99+" else "${badge.count}",
        color = badge.source.onColor(c),
        fontSize = if (small) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(CircleShape)
            .background(badge.source.color(c))
            .border(if (small) 1.dp else 2.dp, c.surface, CircleShape)
            .padding(horizontal = if (small) 5.dp else 8.dp, vertical = if (small) 0.dp else 2.dp),
    )
}

/**
 * A second strip under the bar, built like it: the newest alert, then one capsule per source
 * with its count. Tap a capsule to show only that source's alerts (tap again for all), long-press
 * it to open the app. Tap the strip to drop down the list.
 */
@Composable
fun NotificationBar(
    config: LauncherConfig.Notifications,
    floating: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Noctalia.style
    val c = style.colors
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(NotificationFeed.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = NotificationFeed.hasAccess(context) }
    val alerts by NotificationFeed.alerts.collectAsStateWithLifecycle()
    val sources = Source.entries.filter { it.enabledIn(config) }
    var selected by remember { mutableStateOf<Source?>(null) }
    if (selected != null && selected !in sources) selected = null
    val enabledAlerts = alerts.filter { it.source in sources }
    val shown = selected?.let { s -> enabledAlerts.filter { it.source == s } } ?: enabledAlerts
    LaunchedEffect(shown.isEmpty()) { if (shown.isEmpty()) onExpandedChange(false) }

    val shape = if (floating) RoundedCornerShape(style.radiusXl) else RectangleShape
    fun Modifier.barSurface() = this
        .clip(shape)
        .background(c.surface)
        .then(if (floating) Modifier.border(NoctaliaStyle.borderWidth, c.outlineVariant, shape) else Modifier)

    Column(
        modifier
            .fillMaxWidth()
            .then(if (floating) Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(NoctaliaStyle.barThickness)
                .barSurface()
                .clickable(enabled = hasAccess && shown.isNotEmpty()) { onExpandedChange(!expanded) }
                .padding(horizontal = NoctaliaStyle.barCapsulePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!hasAccess) {
                Capsule { Icon(NIcons.Bell, null, tint = c.primary, modifier = Modifier.size(15.dp)) }
                Text("Allow notification access for alerts", color = c.onSurfaceVariant, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Capsule(fill = c.primary, onClick = {
                    runCatching { context.startActivity(NotificationFeed.accessSettingsIntent(context)) }
                }) { Text("Allow", color = c.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                return@Row
            }
            Capsule(padding = 8.dp) {
                Icon(NIcons.Bell, "Notifications", tint = if (shown.isEmpty()) c.onSurfaceVariant else c.primary, modifier = Modifier.size(15.dp))
            }
            Latest(shown.firstOrNull(), selected, Modifier.weight(1f).padding(horizontal = 4.dp))
            sources.forEach { source ->
                val count = enabledAlerts.count { it.source == source }
                val isSelected = source == selected
                Capsule(
                    padding = 8.dp,
                    fill = if (isSelected) source.color(c).copy(alpha = 0.22f) else null,
                    border = if (isSelected) source.color(c) else null,
                    onClick = { selected = if (isSelected) null else source },
                    onLongClick = { NotificationFeed.openSource(context, source, config) },
                ) {
                    val tint = if (count > 0 || isSelected) source.color(c) else c.onSurfaceVariant
                    Icon(source.icon, source.label, tint = tint, modifier = Modifier.size(14.dp))
                    if (count > 0) Text(if (count > 99) "99+" else "$count", color = c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (shown.isNotEmpty()) {
                Icon(if (expanded) NIcons.ChevronUp else NIcons.ChevronDown, if (expanded) "Collapse" else "Expand",
                    tint = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp).size(16.dp))
            }
        }

        AnimatedVisibility(
            expanded && hasAccess && shown.isNotEmpty(),
            enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)) + expandVertically(tween(NoctaliaStyle.ANIM_NORMAL)),
            exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST)) + shrinkVertically(tween(NoctaliaStyle.ANIM_FAST)),
        ) {
            AlertList(
                shown,
                selected,
                onOpenSource = { NotificationFeed.openSource(context, it, config) },
                modifier = Modifier.fillMaxWidth().barSurface(),
            )
        }
    }
}

internal fun Source.enabledIn(config: LauncherConfig.Notifications) = when (this) {
    Source.Slack -> config.slack
    Source.Sms -> config.sms
    Source.Email -> config.email
}

/** The newest alert, one line: a source dot, who/where, then the message. */
@Composable
private fun Latest(alert: Alert?, selected: Source?, modifier: Modifier) {
    val c = Noctalia.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (alert == null) {
            Text(if (selected != null) "No ${selected.label} alerts" else "No new alerts",
                color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Row
        }
        Box(Modifier.size(6.dp).clip(CircleShape).background(alert.source.color(c)))
        Spacer(Modifier.width(6.dp))
        alert.title?.let {
            Text(it, color = c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        alert.text?.let {
            Text(if (alert.title != null) "  $it" else it, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun AlertList(alerts: List<Alert>, selected: Source?, onOpenSource: (Source) -> Unit, modifier: Modifier) {
    val c = Noctalia.colors
    val now = rememberMinuteTicker()
    Column(modifier.padding(NoctaliaStyle.spaceSm)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val what = selected?.let { "${it.label} alert" } ?: "alert"
            Text("${alerts.size} $what${if (alerts.size == 1) "" else "s"}", color = c.onSurfaceVariant, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            selected?.let { source ->
                Text(
                    "Open ${source.label}",
                    color = source.color(c),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenSource(source) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                "Clear all",
                color = c.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { NotificationFeed.dismissAll(alerts) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(
            Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            alerts.forEach { AlertRow(it, now) }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, now: Long) {
    val style = Noctalia.style
    val c = style.colors
    val context = LocalContext.current
    val tint = alert.source.color(c)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.radiusLg))
            .background(c.surfaceContainer)
            .clickable { NotificationFeed.open(context, alert) }
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(c.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(alert.source.icon, alert.source.label, tint = tint, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(alert.title ?: alert.source.label, color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(ageText(now, alert.time), color = c.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
            alert.text?.let {
                Text(it, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.padding(start = 2.dp).size(32.dp).clip(CircleShape).clickable { NotificationFeed.dismiss(alert) },
            contentAlignment = Alignment.Center,
        ) { Icon(NIcons.Close, "Dismiss", tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
    }
}

/** Same capsule as the top bar's; [border] marks the selected source. */
@Composable
private fun Capsule(
    padding: Dp = 10.dp,
    fill: Color? = null,
    border: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Noctalia.colors
    Row(
        Modifier
            .height(NoctaliaStyle.barThickness - NoctaliaStyle.barCapsulePadding * 2)
            .clip(CircleShape)
            .background(fill ?: c.surfaceContainer)
            .then(if (border != null) Modifier.border(NoctaliaStyle.borderWidth, border, CircleShape) else Modifier)
            .then(
                if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier,
            )
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

@Composable
private fun rememberMinuteTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            now = System.currentTimeMillis()
        }
    }
    return now
}
