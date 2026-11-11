package dev.scrollwerk.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import dev.scrollwerk.launcher.ui.theme.Japanese
import dev.scrollwerk.launcher.ui.theme.KanjiBrush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

/** Everything a workspace page can ask the launcher to do. */
class WorkspaceActions(
    val open: (WsColumn) -> Unit,
    val addColumn: (Workspace) -> Unit,
    val split: (Workspace, WsColumn) -> Unit,
    val removeFromColumn: (Workspace, WsColumn, String) -> Unit,
    val move: (Workspace, WsColumn, Int) -> Unit,
    val removeColumn: (Workspace, WsColumn) -> Unit,
    val rename: (Workspace) -> Unit = {},
    /** Whether the column's app has a rule pointing at this workspace; null for split columns. */
    val ruleHere: (Workspace, WsColumn) -> Boolean? = { _, _ -> null },
    val toggleRule: (Workspace, WsColumn) -> Unit = { _, _ -> },
)

/**
 * One niri workspace: a horizontal strip of columns, the focused one centred with a focus
 * ring and the rest dimmed. Tap the focused column to switch to its app(s); tap another to
 * focus it. A trailing "+" column adds an app.
 */
@Composable
fun WorkspacePage(
    number: Int,
    workspace: Workspace,
    appsById: Map<String, AppEntry>,
    icon: suspend (String) -> ImageBitmap?,
    actions: WorkspaceActions,
    badge: (WsColumn) -> Badge? = { null },
) {
    val c = Noctalia.colors
    val isDraft = workspace.id == WorkspacesState.DRAFT_ID
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focused by remember { derivedStateOf { state.centeredIndex() } }

    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(Noctalia.style.radiusMd))
                .clickable(enabled = !isDraft) { actions.rename(workspace) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KanjiNumeral(number, isDraft, fontSize = 20.sp)
            Text(
                workspaceTitle(workspace, number, isDraft),
                color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (!isDraft) {
                Spacer(Modifier.width(6.dp))
                Icon(NIcons.Pencil, "Rename workspace", tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(workspaceSubtitle(workspace, number, isDraft), color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columnWidth = maxWidth * 0.66f
            val side = (maxWidth - columnWidth) / 2
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
                contentPadding = PaddingValues(horizontal = side, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(workspace.columns, key = { _, col -> col.id }) { i, column ->
                    ColumnCard(
                        column = column,
                        appsById = appsById,
                        focused = i == focused,
                        canMoveLeft = i > 0,
                        canMoveRight = i < workspace.columns.lastIndex,
                        icon = icon,
                        badge = badge(column),
                        onClick = { if (i == focused) actions.open(column) else scope.launch { state.animateScrollToItem(i) } },
                        onSplit = { actions.split(workspace, column) },
                        onRemoveApp = { actions.removeFromColumn(workspace, column, it) },
                        onMove = { delta ->
                            actions.move(workspace, column, delta)
                            scope.launch { state.animateScrollToItem((i + delta).coerceAtLeast(0)) }
                        },
                        onRemove = { actions.removeColumn(workspace, column) },
                        ruleHere = actions.ruleHere(workspace, column),
                        onToggleRule = { actions.toggleRule(workspace, column) },
                        modifier = Modifier.width(columnWidth).fillParentMaxHeight(),
                    )
                }
                item(key = "add") {
                    AddColumnCard(
                        focused = focused == workspace.columns.size,
                        modifier = Modifier.width(columnWidth).fillParentMaxHeight(),
                    ) {
                        if (focused == workspace.columns.size) actions.addColumn(workspace)
                        else scope.launch { state.animateScrollToItem(workspace.columns.size) }
                    }
                }
            }
        }
    }
}

/** Kanagawa writes the workspace's number in brush kanji (一, 二, 三…) in front of its title. */
@Composable
internal fun KanjiNumeral(number: Int, isDraft: Boolean, fontSize: androidx.compose.ui.unit.TextUnit) {
    if (isDraft || !Noctalia.style.extras.kanji) return
    Text(
        Japanese.numeral(number),
        color = Noctalia.colors.primary,
        fontSize = fontSize,
        fontFamily = KanjiBrush,
        maxLines = 1,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** "Chat", or "Workspace 3" when it has no name. */
internal fun workspaceTitle(workspace: Workspace, number: Int, isDraft: Boolean) = when {
    isDraft -> "New workspace"
    workspace.name != null -> workspace.name
    else -> "Workspace $number"
}

/** Column count; a named workspace also shows its number, since the dots in the bar count by number. */
internal fun workspaceSubtitle(workspace: Workspace, number: Int, isDraft: Boolean): String {
    if (isDraft) return "add an app to start it"
    val count = "${workspace.columns.size} column${if (workspace.columns.size == 1) "" else "s"}"
    return if (workspace.name != null) "$number · $count" else count
}

/** Names a workspace: a Noctalia-styled dialog with one field. Save with Enter or the button. */
@Composable
internal fun RenameWorkspaceDialog(current: String?, number: Int, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    var text by remember { mutableStateOf(TextFieldValue(current.orEmpty(), TextRange(0, current.orEmpty().length))) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(style.radiusXl)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .border(NoctaliaStyle.borderWidth, c.outlineVariant, shape)
                .padding(NoctaliaStyle.panelPadding),
            verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceMd),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(NIcons.Pencil, null, tint = c.primary, modifier = Modifier.size(16.dp))
                Text("Name workspace $number", color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            val fieldShape = RoundedCornerShape(style.radiusLg)
            BasicTextField(
                value = text,
                onValueChange = { if (it.text.length <= WorkspacesState.MAX_NAME) text = it },
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.merge(TextStyle(color = c.onSurface, fontSize = 16.sp)),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(NoctaliaStyle.controlHeightLg)
                            .clip(fieldShape)
                            .background(c.surfaceContainer)
                            .border(NoctaliaStyle.borderWidth, c.primary, fieldShape)
                            .padding(horizontal = NoctaliaStyle.spaceMd),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.text.isEmpty()) Text("Workspace $number", color = c.onSurfaceVariant, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) TextButton("Remove name") { onSave(null) }
                Spacer(Modifier.weight(1f))
                TextButton("Cancel", onDismiss)
                Spacer(Modifier.width(4.dp))
                PillButton("Save") { onSave(text.text) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    val c = Noctalia.colors
    Text(
        text, color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: 0
}

@Composable
private fun StripCard(focused: Boolean, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    val shape = RoundedCornerShape(style.radiusXl)
    val alpha by animateFloatAsState(if (focused) 1f else 0.6f, label = "column-alpha")
    val ring by animateDpAsState(if (focused) 2.dp else NoctaliaStyle.borderWidth, label = "focus-ring")
    Column(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(c.surface.copy(alpha = 0.94f))
            .border(ring, if (focused) c.primary else c.outlineVariant, shape),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnCard(
    column: WsColumn,
    appsById: Map<String, AppEntry>,
    focused: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    icon: suspend (String) -> ImageBitmap?,
    badge: Badge?,
    onClick: () -> Unit,
    onSplit: () -> Unit,
    onRemoveApp: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    ruleHere: Boolean?,
    onToggleRule: () -> Unit,
    modifier: Modifier,
) {
    val c = Noctalia.colors
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        StripCard(focused, Modifier.fillMaxSize().combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
            column.apps.forEachIndexed { i, id ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(NoctaliaStyle.borderWidth).background(c.outlineVariant))
                AppSlot(appsById[id], id, icon, large = !column.isSplit, modifier = Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (column.isSplit) NIcons.Split else NIcons.Columns, null, tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    when {
                        !focused -> "Tap to focus"
                        column.isSplit -> "Tap to open split screen"
                        else -> "Tap to open"
                    },
                    color = c.onSurfaceVariant, fontSize = 12.sp,
                )
            }
        }
        badge?.let { CountBadge(it, Modifier.align(Alignment.TopEnd).padding(10.dp)) }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = c.surfaceContainer,
            shape = RoundedCornerShape(Noctalia.style.radiusLg),
        ) {
            fun item(label: String, icon: ImageVector, action: () -> Unit) = @Composable {
                DropdownMenuItem(
                    text = { Text(label, color = c.onSurface) },
                    leadingIcon = { Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; action() },
                )
            }
            if (!column.isSplit) item("Add app below (split screen)", NIcons.Split, onSplit)()
            else column.apps.forEach { id ->
                item("Remove ${appsById[id]?.label ?: "app"}", NIcons.Trash) { onRemoveApp(id) }()
            }
            if (canMoveLeft) item("Move left", NIcons.ArrowLeft) { onMove(-1) }()
            if (canMoveRight) item("Move right", NIcons.ArrowRight) { onMove(+1) }()
            ruleHere?.let { on ->
                item(if (on) "Stop always putting it here" else "Always put in this workspace", NIcons.Pin, onToggleRule)()
            }
            item("Remove column", NIcons.Trash, onRemove)()
        }
    }
}

@Composable
private fun AppSlot(app: AppEntry?, id: String, icon: suspend (String) -> ImageBitmap?, large: Boolean, modifier: Modifier) {
    val c = Noctalia.colors
    val bitmap by produceState<ImageBitmap?>(null, id, icon) { value = icon(id) }
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(if (large) 88.dp else 60.dp)) {
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            app?.label ?: "Unavailable",
            color = c.onSurface,
            fontSize = if (large) 18.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddColumnCard(focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Noctalia.colors
    StripCard(focused, modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.surfaceContainer), contentAlignment = Alignment.Center) {
                Icon(NIcons.Plus, null, tint = c.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Add app", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(if (focused) "Tap to choose" else "Tap to focus", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/**
 * Noctalia's workspaces bar widget: one dot per page (desktop, each workspace, and the
 * trailing empty one); the active page is a wide pill. Long-press opens the Overview.
 */
@Composable
fun WorkspaceDots(pageCount: Int, current: Int, onSelect: (Int) -> Unit, onLongPress: (() -> Unit)? = null) {
    val c = Noctalia.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { page ->
            val active = page == current
            val width by animateDpAsState(if (active) 16.dp else 6.dp, label = "dot")
            val isDraft = page == pageCount - 1 && page != 0
            Box(
                Modifier
                    .clip(CircleShape)
                    .combinedClickable(onClick = { onSelect(page) }, onLongClick = onLongPress)
                    .padding(horizontal = 3.dp, vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> c.primary
                                isDraft -> c.onSurfaceVariant.copy(alpha = 0.4f)
                                else -> c.onSurfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

