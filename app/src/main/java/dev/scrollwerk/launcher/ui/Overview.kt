package dev.scrollwerk.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scrollwerk.launcher.data.AppEntry
import dev.scrollwerk.launcher.data.Workspace
import dev.scrollwerk.launcher.data.WorkspacesState
import dev.scrollwerk.launcher.data.WsColumn
import dev.scrollwerk.launcher.notifications.Badge
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import kotlin.math.roundToInt

private val MiniWidth = 52.dp
private val MiniHeight = 76.dp

/** A column being dragged in the Overview; [pointer] is in root coordinates. */
private data class Drag(val workspaceId: String, val column: WsColumn, val pointer: Offset)

/**
 * niri's Overview, phone-sized: every workspace at once as a strip of mini columns. Tap one to
 * go there; long-press a column and drag it to move it, to another workspace, or onto
 * "New workspace". The arrows reorder workspaces.
 */
@Composable
fun Overview(
    visible: Boolean,
    workspaces: WorkspacesState,
    currentPage: Int,
    appsById: Map<String, AppEntry>,
    icon: suspend (String) -> ImageBitmap?,
    badge: (WsColumn) -> Badge?,
    onGoToPage: (Int) -> Unit,
    onEdit: ((WorkspacesState) -> WorkspacesState) -> Unit,
    onRename: (Workspace) -> Unit,
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

    // Where each workspace card and mini column sits on screen, for dropping.
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hoverId = drag?.let { d -> cardBounds.entries.firstOrNull { it.value.contains(d.pointer) }?.key }

    fun drop(d: Drag) {
        val target = cardBounds.entries.firstOrNull { it.value.contains(d.pointer) }?.key ?: return
        val targetColumns = workspaces.displayed.firstOrNull { it.id == target }?.columns.orEmpty()
            .filter { it.id != d.column.id }
        val index = targetColumns.count { col -> columnBounds[col.id]?.let { it.center.x < d.pointer.x } == true }
        onEdit { it.moveColumnTo(d.workspaceId, d.column.id, target, index) }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
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
                enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)) + scaleIn(tween(NoctaliaStyle.ANIM_NORMAL), initialScale = 1.08f),
                exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST)) + scaleOut(tween(NoctaliaStyle.ANIM_FAST), targetScale = 1.08f),
            ) {
                val shape = RoundedCornerShape(style.radiusXl)
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = 760.dp)
                        .clip(shape)
                        .background(c.surface)
                        .border(NoctaliaStyle.borderWidth, c.outlineVariant, shape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState(), enabled = drag == null)
                        .padding(NoctaliaStyle.panelPadding),
                    verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(NIcons.Columns, null, tint = c.primary, modifier = Modifier.size(16.dp))
                        Text("Overview", color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Tap to go there. Long-press a column and drag it to move it.", color = c.onSurfaceVariant, fontSize = 12.sp)

                    DesktopCard(selected = currentPage == 0) { onGoToPage(0) }
                    val stored = workspaces.workspaces
                    workspaces.displayed.forEachIndexed { i, ws ->
                        val isDraft = ws.id == WorkspacesState.DRAFT_ID
                        WorkspaceCard(
                            number = i + 1,
                            workspace = ws,
                            isDraft = isDraft,
                            selected = currentPage == i + 1,
                            dropTarget = drag != null && hoverId == ws.id,
                            dragging = drag?.column?.id,
                            canMoveUp = !isDraft && i > 0,
                            canMoveDown = !isDraft && i < stored.lastIndex,
                            appsById = appsById,
                            icon = icon,
                            badge = badge,
                            onClick = { onGoToPage(i + 1) },
                            onMove = { delta -> onEdit { it.moveWorkspace(ws.id, delta) } },
                            onRename = { onRename(ws) },
                            onBounds = { cardBounds[ws.id] = it },
                            onColumnBounds = { id, r -> columnBounds[id] = r },
                            onDragStart = { col, at -> drag = Drag(ws.id, col, at) },
                            onDrag = { delta -> drag = drag?.let { it.copy(pointer = it.pointer + delta) } },
                            onDragEnd = { drag?.let(::drop); drag = null },
                            onDragCancel = { drag = null },
                        )
                    }
                }
            }
        }

        // The column under your finger while dragging.
        drag?.let { d ->
            val ghostOffset = d.pointer - origin
            Box(
                Modifier
                    .offset { IntOffset((ghostOffset.x - MiniWidth.toPx() / 2).roundToInt(), (ghostOffset.y - MiniHeight.toPx() / 2).roundToInt()) }
                    .graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; shadowElevation = 12.dp.toPx(); shape = RoundedCornerShape(style.radiusLg); clip = true },
            ) { MiniColumn(d.column, appsById, icon, highlighted = true) }
        }
    }
}

@Composable
private fun DesktopCard(selected: Boolean, onClick: () -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    val shape = RoundedCornerShape(style.radiusLg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceContainer)
            .border(if (selected) 2.dp else NoctaliaStyle.borderWidth, if (selected) c.primary else c.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NIcons.Sun, null, tint = c.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Desktop", color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("widgets", color = c.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun WorkspaceCard(
    number: Int,
    workspace: Workspace,
    isDraft: Boolean,
    selected: Boolean,
    dropTarget: Boolean,
    dragging: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    appsById: Map<String, AppEntry>,
    icon: suspend (String) -> ImageBitmap?,
    badge: (WsColumn) -> Badge?,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onBounds: (Rect) -> Unit,
    onColumnBounds: (String, Rect) -> Unit,
    onDragStart: (WsColumn, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val style = Noctalia.style
    val c = style.colors
    val shape = RoundedCornerShape(style.radiusLg)
    val ring = when {
        dropTarget -> c.tertiary
        selected -> c.primary
        else -> c.outlineVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clip(shape)
            .background(if (dropTarget) c.surfaceContainerHigh else c.surfaceContainer)
            .border(if (selected || dropTarget) 2.dp else NoctaliaStyle.borderWidth, ring, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(workspaceTitle(workspace, number, isDraft), color = c.onSurface, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isDraft) "drop a column here" else workspaceSubtitle(workspace, number, false),
                color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (!isDraft) SmallIconButton(NIcons.Pencil, "Rename workspace", onRename)
            if (canMoveUp) SmallIconButton(NIcons.ChevronUp, "Move workspace up") { onMove(-1) }
            if (canMoveDown) SmallIconButton(NIcons.ChevronDown, "Move workspace down") { onMove(+1) }
        }
        if (!isDraft) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = dragging == null),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                workspace.columns.forEach { col ->
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    val latestBounds by rememberUpdatedState(bounds)
                    Box(
                        Modifier
                            .onGloballyPositioned { bounds = it.boundsInRoot(); onColumnBounds(col.id, bounds) }
                            .graphicsLayer { alpha = if (dragging == col.id) 0.3f else 1f }
                            .pointerInput(col.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at -> onDragStart(col, latestBounds.topLeft + at) },
                                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            },
                    ) {
                        MiniColumn(col, appsById, icon, highlighted = false)
                        badge(col)?.let { CountBadge(it, Modifier.align(Alignment.TopEnd).padding(3.dp), small = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniColumn(column: WsColumn, appsById: Map<String, AppEntry>, icon: suspend (String) -> ImageBitmap?, highlighted: Boolean) {
    val style = Noctalia.style
    val c = style.colors
    val shape = RoundedCornerShape(style.radiusLg)
    Column(
        Modifier
            .size(MiniWidth, MiniHeight)
            .clip(shape)
            .background(c.surface)
            .border(NoctaliaStyle.borderWidth, if (highlighted) c.primary else c.outlineVariant, shape)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        column.apps.forEach { id ->
            val bitmap by produceState<ImageBitmap?>(null, id, icon) { value = icon(id) }
            Box(Modifier.size(if (column.isSplit) 24.dp else 32.dp)) {
                bitmap?.let { Image(it, contentDescription = appsById[id]?.label, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Noctalia.colors
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
}

/** Calls [onPinch] when two fingers pinch in; one-finger scrolling and taps pass through untouched. */
fun Modifier.onPinchIn(threshold: Float = 0.7f, onPinch: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var start: Float? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.filter { it.pressed }
                if (down.isEmpty()) break
                if (down.size < 2) { start = null; continue }
                val distance = (down[0].position - down[1].position).getDistance()
                val from = start ?: distance.also { start = it }
                if (distance < from * threshold) {
                    event.changes.forEach { it.consume() }
                    onPinch()
                    break
                }
            }
        }
    },
)
