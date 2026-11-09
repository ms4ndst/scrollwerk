package dev.scrollwerk.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.config.SearchPosition
import dev.scrollwerk.launcher.data.AppEntry
import dev.scrollwerk.launcher.data.UsageState
import dev.scrollwerk.launcher.search.AppFilter
import dev.scrollwerk.launcher.search.AppResult
import dev.scrollwerk.launcher.search.AppSearch
import dev.scrollwerk.launcher.search.Calculator
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle

/** An extra entry in a result's long-press menu. */
class RowAction(val label: String, val run: (AppEntry) -> Unit)

private sealed interface ResultItem {
    data class Calc(val value: String) : ResultItem
    data class App(val result: AppResult) : ResultItem
}

/**
 * The launcher: a rofi-style prompt over a ranked result list, styled like Noctalia's
 * Simple launcher layout. Type to fuzzy-filter, Enter / Go launches the selected row,
 * arrows or Tab move the selection, Esc / Back closes.
 */
@Composable
fun LauncherPanel(
    visible: Boolean,
    apps: List<AppEntry>,
    usage: UsageState,
    config: LauncherConfig.Launcher,
    icon: suspend (String) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    /** Prompt text; changes when the launcher is used to pick an app for a workspace. */
    placeholder: String = "Search apps, or type 12*7…",
    /** Extra long-press action, e.g. "Add to workspace 2". */
    extraAction: RowAction? = null,
) {
    val style = Noctalia.style
    val c = style.colors
    val atBottom = config.searchPosition == SearchPosition.Bottom
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<AppFilter>(AppFilter.All) }
    var selected by remember { mutableIntStateOf(0) }

    // Every open starts fresh, like rofi.
    LaunchedEffect(visible) {
        if (!visible) { query = ""; filter = AppFilter.All }
    }

    val rows: List<ResultItem> = remember(apps, query, filter, usage, config) {
        val calc = if (config.calculator) Calculator.evaluate(query)?.let(ResultItem::Calc) else null
        listOfNotNull(calc) + AppSearch.search(apps, query, usage, filter, config.sortByUsage).map(ResultItem::App)
    }
    LaunchedEffect(query, filter) { selected = 0 }
    val selectedIndex = selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    val context = LocalContext.current
    fun activate(row: ResultItem?) {
        when (row) {
            is ResultItem.App -> onLaunch(row.result.app)
            is ResultItem.Calc -> {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Result", row.value))
                onDismiss()
            }
            null -> Unit
        }
    }

    // Scrim
    AnimatedVisibility(visible, enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)), exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }

    // Panel
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(12.dp)
            // Stay clear of the bar so it remains visible above the scrim.
            .padding(top = NoctaliaStyle.barThickness + 12.dp),
        contentAlignment = if (atBottom) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)) +
                slideInVertically(tween(NoctaliaStyle.ANIM_NORMAL)) { if (atBottom) it / 8 else -it / 8 },
            exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST)) +
                slideOutVertically(tween(NoctaliaStyle.ANIM_FAST)) { if (atBottom) it / 16 else -it / 16 },
        ) {
            val shape = RoundedCornerShape(style.radiusXl)
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(shape)
                    .background(c.surface)
                    .border(NoctaliaStyle.borderWidth, c.outlineVariant, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {} // don't dismiss
                    .padding(NoctaliaStyle.panelPadding),
                verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
            ) {
                val prompt = @Composable {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        visible = visible,
                        onActivate = { activate(rows.getOrNull(selectedIndex)) },
                        onMove = { delta -> if (rows.isNotEmpty()) selected = (selectedIndex + delta).mod(rows.size) },
                        onEscape = onDismiss,
                        upIsNext = false,
                        placeholder = placeholder,
                    )
                }
                val chips = @Composable {
                    if (config.categories) FilterChips(apps, usage, filter) { filter = it }
                }
                if (!atBottom) { prompt(); chips() }
                else chips()
                Results(
                    rows = rows,
                    selectedIndex = selectedIndex,
                    // Top-down (best match / A first), like rofi; the panel shrinks to fit few results.
                    reverse = false,
                    icon = icon,
                    onActivate = { i -> activate(rows[i]) },
                    onTogglePin = onTogglePin,
                    onAppInfo = onAppInfo,
                    extraAction = extraAction,
                )
                if (atBottom) prompt()
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    visible: Boolean,
    onActivate: () -> Unit,
    onMove: (Int) -> Unit,
    onEscape: () -> Unit,
    upIsNext: Boolean,
    placeholder: String,
) {
    val style = Noctalia.style
    val c = style.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val shape = RoundedCornerShape(style.radiusLg)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onActivate() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val next = if (upIsNext) -1 else 1
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> onActivate()
                    Key.DirectionDown -> onMove(next)
                    Key.DirectionUp -> onMove(-next)
                    Key.Tab -> onMove(1)
                    Key.Escape -> onEscape()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(NoctaliaStyle.controlHeightLg)
                    .clip(shape)
                    .background(c.surfaceContainer)
                    .border(NoctaliaStyle.borderWidth, if (focused) c.primary else c.outlineVariant, shape)
                    .padding(horizontal = NoctaliaStyle.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NIcons.Search, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NoctaliaStyle.spaceSm))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, color = c.onSurfaceVariant, fontSize = 16.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun FilterChips(apps: List<AppEntry>, usage: UsageState, filter: AppFilter, onSelect: (AppFilter) -> Unit) {
    val categories = remember(apps) { AppSearch.categories(apps) }
    val options = buildList {
        add(AppFilter.All to "All")
        if (usage.recent.isNotEmpty()) add(AppFilter.Recent to "Recent")
        categories.forEach { add(AppFilter.Category(it) to it) }
    }
    if (options.size <= 1) return
    val c = Noctalia.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options, key = { it.second }) { (option, label) ->
            val active = option == filter
            Text(
                label,
                color = if (active) c.onPrimary else c.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) c.primary else c.surfaceContainer)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.Results(
    rows: List<ResultItem>,
    selectedIndex: Int,
    reverse: Boolean,
    icon: suspend (String) -> ImageBitmap?,
    onActivate: (Int) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    extraAction: RowAction?,
) {
    val c = Noctalia.colors
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, rows.size) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == selectedIndex && it.offset >= 0 }) listState.scrollToItem(selectedIndex)
    }
    if (rows.isEmpty()) {
        Text(
            "No results",
            color = c.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(NoctaliaStyle.spaceMd),
        )
        return
    }
    LazyColumn(
        state = listState,
        reverseLayout = reverse,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.weight(1f, fill = false),
    ) {
        itemsIndexed(rows, key = { _, row -> if (row is ResultItem.App) row.result.app.id else "calc" }) { i, row ->
            when (row) {
                is ResultItem.Calc -> CalcRow(row.value, i == selectedIndex) { onActivate(i) }
                is ResultItem.App -> AppRow(row.result, i == selectedIndex, icon, { onActivate(i) }, onTogglePin, onAppInfo, extraAction)
            }
        }
    }
}

@Composable
private fun ResultRowFrame(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val style = Noctalia.style
    @OptIn(ExperimentalFoundationApi::class)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(style.radiusLg))
            .background(if (selected) style.colors.hover else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun AppRow(
    result: AppResult,
    selected: Boolean,
    icon: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    extraAction: RowAction?,
) {
    val c = Noctalia.colors
    val app = result.app
    val bitmap by produceState<ImageBitmap?>(null, app.id) { value = icon(app.id) }
    var menu by remember { mutableStateOf(false) }
    val fg = if (selected) c.onHover else c.onSurface
    Box {
        ResultRowFrame(selected, onClick, onLongClick = { menu = true }) {
            Box(Modifier.size(32.dp)) {
                bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(NoctaliaStyle.spaceMd))
            Text(
                highlight(app.label, result.matched, if (selected) c.onHover else c.primary),
                color = fg,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (result.pinned) {
                Icon(NIcons.Pin, contentDescription = "Pinned", tint = if (selected) c.onHover else c.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = c.surfaceContainer,
            shape = RoundedCornerShape(Noctalia.style.radiusLg),
        ) {
            DropdownMenuItem(
                text = { Text(if (result.pinned) "Unpin" else "Pin", color = c.onSurface) },
                leadingIcon = { Icon(NIcons.Pin, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; onTogglePin(app) },
            )
            DropdownMenuItem(
                text = { Text("App info", color = c.onSurface) },
                leadingIcon = { Icon(NIcons.Info, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; onAppInfo(app) },
            )
            extraAction?.let { action ->
                DropdownMenuItem(
                    text = { Text(action.label, color = c.onSurface) },
                    leadingIcon = { Icon(NIcons.Plus, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; action.run(app) },
                )
            }
        }
    }
}

@Composable
private fun CalcRow(value: String, selected: Boolean, onClick: () -> Unit) {
    val c = Noctalia.colors
    val fg = if (selected) c.onHover else c.onSurface
    ResultRowFrame(selected, onClick) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(NIcons.Calculator, contentDescription = null, tint = if (selected) c.onHover else c.secondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(NoctaliaStyle.spaceMd))
        Text("= $value", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        Text("Enter to copy", color = if (selected) c.onHover else c.onSurfaceVariant, fontSize = 12.sp)
    }
}

/** Bold + accent colour on the characters the fuzzy matcher hit, like rofi's highlight. */
private fun highlight(label: String, matched: IntArray, color: Color): AnnotatedString = buildAnnotatedString {
    if (matched.isEmpty()) { append(label); return@buildAnnotatedString }
    val hits = matched.toHashSet()
    label.forEachIndexed { i, ch ->
        if (i in hits) withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(ch) } else append(ch)
    }
}
