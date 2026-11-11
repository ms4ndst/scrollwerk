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
import dev.scrollwerk.launcher.search.EmojiEntry
import dev.scrollwerk.launcher.search.EmojiSearch
import dev.scrollwerk.launcher.search.UnitConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.scrollwerk.launcher.search.ContactEntry
import dev.scrollwerk.launcher.search.ContactSearch
import dev.scrollwerk.launcher.search.Scored
import dev.scrollwerk.launcher.search.SettingsPage
import dev.scrollwerk.launcher.search.SettingsSearch
import dev.scrollwerk.launcher.search.WebSearch
import dev.scrollwerk.launcher.data.ContactsRepository
import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import dev.scrollwerk.launcher.search.ShortcutEntry
import dev.scrollwerk.launcher.search.ShortcutResult
import dev.scrollwerk.launcher.search.ShortcutSearch
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle

/** An extra entry in a result's long-press menu; its label can depend on the app ("Add to Chat"). */
class RowAction(val label: (AppEntry) -> String, val run: (AppEntry) -> Unit)

private sealed interface ResultItem {
    data class Calc(val value: String) : ResultItem
    data class App(val result: AppResult) : ResultItem
    data class Shortcut(val result: ShortcutResult) : ResultItem
    data class Setting(val result: Scored<SettingsPage>) : ResultItem
    data class Contact(val result: Scored<ContactEntry>) : ResultItem
    data object AllowContacts : ResultItem
    data class Web(val query: String) : ResultItem
    data class Convert(val conversion: UnitConverter.Conversion) : ResultItem
    data class Emoji(val result: Scored<EmojiEntry>) : ResultItem
}

/** The bundled emoji list, minus any this phone's font can't draw. Loaded once, on first use. */
private object EmojiAssets {
    @Volatile private var cache: List<EmojiEntry>? = null

    suspend fun load(context: android.content.Context): List<EmojiEntry> = cache ?: withContext(Dispatchers.IO) {
        val paint = android.graphics.Paint()
        val all = runCatching { context.assets.open("emoji.tsv").bufferedReader().use { it.readText() } }.getOrDefault("")
        EmojiSearch.parse(all).filter { paint.hasGlyph(it.emoji) }.also { cache = it }
    }
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
    placeholder: String = "Search apps, settings, contacts…",
    /** Extra long-press action, e.g. "Add to workspace 2". */
    extraAction: RowAction? = null,
    /** App shortcuts to search and list in long-press menus; empty to leave them out. */
    shortcuts: List<ShortcutEntry> = emptyList(),
    shortcutIcon: suspend (String) -> ImageBitmap? = { null },
    onLaunchShortcut: (ShortcutEntry) -> Unit = {},
    /** Settings, contacts and web search rows; off while picking an app. */
    providers: Boolean = true,
    /** Called when contacts permission is refused, so the launcher stops offering it. */
    onDisableContacts: () -> Unit = {},
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

    val context = LocalContext.current
    val contactsRepo = remember(context) { ContactsRepository(context) }
    var contactsGranted by remember { mutableStateOf(contactsRepo.hasPermission()) }
    LaunchedEffect(visible) { if (visible) contactsGranted = contactsRepo.hasPermission() }
    val wantContacts = providers && config.contacts
    val contacts by produceState(emptyList<ContactEntry>(), visible, contactsGranted, wantContacts) {
        if (visible && contactsGranted && wantContacts) value = contactsRepo.load()
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        contactsGranted = ok
        if (!ok) onDisableContacts()
    }

    val emojiQuery = if (config.emoji && providers) EmojiSearch.query(query) else null
    val emojis by produceState(emptyList<EmojiEntry>(), emojiQuery != null) {
        if (emojiQuery != null) value = EmojiAssets.load(context)
    }

    val appLabels = remember(apps) { apps.associate { it.id to it.label } }
    val shortcutsByApp = remember(shortcuts) { shortcuts.groupBy { it.appId } }
    val rows: List<ResultItem> = remember(apps, query, filter, usage, config, shortcuts, emojis, contacts, contactsGranted) {
        // ':' switches to emoji only, like rofi's modes.
        if (emojiQuery != null) return@remember EmojiSearch.search(emojis, emojiQuery).map(ResultItem::Emoji)
        val calc: ResultItem? = (if (config.calculator) Calculator.evaluate(query)?.let(ResultItem::Calc) else null)
            ?: (if (config.units) UnitConverter.evaluate(query)?.let(ResultItem::Convert) else null)
        // Shortcuts only in the All view: the other chips are about apps.
        val all = filter == AppFilter.All
        val actions = if (all) ShortcutSearch.search(shortcuts, appLabels, query).map(ResultItem::Shortcut) else emptyList()
        val extra = all && providers && query.trim().length >= SettingsSearch.MIN_QUERY
        val settings = if (extra && config.settings) SettingsSearch.search(query).map(ResultItem::Setting) else emptyList()
        val people = when {
            !extra || !wantContacts -> emptyList()
            contactsGranted -> ContactSearch.search(contacts, query).map(ResultItem::Contact)
            else -> listOf(ResultItem.AllowContacts)
        }
        val web = if (extra && config.webSearch) listOf(ResultItem.Web(query.trim())) else emptyList()
        listOfNotNull(calc) + AppSearch.search(apps, query, usage, filter, config.sortByUsage).map(ResultItem::App) +
            settings + people + actions + web
    }
    LaunchedEffect(query, filter) { selected = 0 }
    val selectedIndex = selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Scrollwerk", text))
        onDismiss()
    }

    fun start(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    fun activate(row: ResultItem?) {
        when (row) {
            is ResultItem.App -> onLaunch(row.result.app)
            is ResultItem.Shortcut -> onLaunchShortcut(row.result.shortcut)
            is ResultItem.Setting -> {
                if (!start(Intent(row.result.item.action))) start(Intent(Settings.ACTION_SETTINGS))
                onDismiss()
            }
            is ResultItem.Contact -> { contactsRepo.open(row.result.item); onDismiss() }
            is ResultItem.Convert -> copy(row.conversion.number)
            is ResultItem.Emoji -> copy(row.result.item.emoji)
            ResultItem.AllowContacts -> askContacts.launch(Manifest.permission.READ_CONTACTS)
            is ResultItem.Web -> {
                val custom = config.webSearchUrl?.takeIf { "%s" in it }
                val opened = if (custom != null) start(Intent(Intent.ACTION_VIEW, Uri.parse(WebSearch.url(custom, row.query))))
                else start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, row.query)) ||
                    start(Intent(Intent.ACTION_VIEW, Uri.parse(WebSearch.url(DEFAULT_WEB_SEARCH, row.query))))
                if (opened) onDismiss()
            }
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
                    shortcutsByApp = shortcutsByApp,
                    shortcutIcon = shortcutIcon,
                    onLaunchShortcut = onLaunchShortcut,
                    contactsRepo = contactsRepo,
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
    shortcutsByApp: Map<String, List<ShortcutEntry>>,
    shortcutIcon: suspend (String) -> ImageBitmap?,
    onLaunchShortcut: (ShortcutEntry) -> Unit,
    contactsRepo: ContactsRepository,
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
        itemsIndexed(rows, key = { _, row ->
            when (row) {
                is ResultItem.App -> row.result.app.id
                is ResultItem.Shortcut -> "shortcut:" + row.result.shortcut.id
                is ResultItem.Setting -> "setting:" + row.result.item.action
                is ResultItem.Contact -> "contact:" + row.result.item.id
                ResultItem.AllowContacts -> "allow-contacts"
                is ResultItem.Web -> "web"
                is ResultItem.Convert -> "convert"
                is ResultItem.Emoji -> "emoji:" + row.result.item.emoji
                is ResultItem.Calc -> "calc"
            }
        }) { i, row ->
            when (row) {
                is ResultItem.Calc -> CalcRow(row.value, i == selectedIndex) { onActivate(i) }
                is ResultItem.Convert -> CalcRow(row.conversion.text, i == selectedIndex) { onActivate(i) }
                is ResultItem.Emoji -> EmojiRow(row.result, i == selectedIndex) { onActivate(i) }
                is ResultItem.App -> AppRow(
                    row.result, i == selectedIndex, icon, { onActivate(i) }, onTogglePin, onAppInfo, extraAction,
                    shortcutsByApp[row.result.app.id].orEmpty(), shortcutIcon, onLaunchShortcut,
                )
                is ResultItem.Shortcut -> ShortcutRow(row.result, i == selectedIndex, shortcutIcon) { onActivate(i) }
                is ResultItem.Setting -> SimpleRow(
                    NIcons.Settings, highlight(row.result.item.label, row.result.matched, if (i == selectedIndex) c.onHover else c.primary),
                    "Settings", i == selectedIndex,
                ) { onActivate(i) }
                is ResultItem.Contact -> ContactRow(row.result, i == selectedIndex, contactsRepo) { onActivate(i) }
                ResultItem.AllowContacts -> SimpleRow(NIcons.User, AnnotatedString("Search contacts too"), "Tap to allow", i == selectedIndex) { onActivate(i) }
                is ResultItem.Web -> SimpleRow(NIcons.Globe, AnnotatedString("Search the web for “${row.query}”"), "Web", i == selectedIndex) { onActivate(i) }
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
    appShortcuts: List<ShortcutEntry>,
    shortcutIcon: suspend (String) -> ImageBitmap?,
    onLaunchShortcut: (ShortcutEntry) -> Unit,
) {
    val c = Noctalia.colors
    val app = result.app
    val bitmap by produceState<ImageBitmap?>(null, app.id, icon) { value = icon(app.id) }
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
            // The app's own shortcuts first, like any launcher's long-press menu.
            appShortcuts.take(MENU_SHORTCUTS).forEach { shortcut ->
                val sIcon by produceState<ImageBitmap?>(null, shortcut.id, shortcutIcon) { value = shortcutIcon(shortcut.id) }
                DropdownMenuItem(
                    text = { Text(shortcut.label, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Box(Modifier.size(20.dp)) { sIcon?.let { Image(it, null, modifier = Modifier.fillMaxSize()) } }
                    },
                    onClick = { menu = false; onLaunchShortcut(shortcut) },
                )
            }
            if (appShortcuts.isNotEmpty()) {
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(NoctaliaStyle.borderWidth).background(c.outlineVariant))
            }
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
                    text = { Text(action.label(app), color = c.onSurface) },
                    leadingIcon = { Icon(NIcons.Plus, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; action.run(app) },
                )
            }
        }
    }
}

private const val MENU_SHORTCUTS = 6
private const val DEFAULT_WEB_SEARCH = "https://duckduckgo.com/?q=%s"

/** A provider row: an outline icon, the text, and what kind of result it is on the right. */
@Composable
private fun SimpleRow(icon: ImageVector, text: AnnotatedString, kind: String, selected: Boolean, onClick: () -> Unit) {
    val c = Noctalia.colors
    ResultRowFrame(selected, onClick) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) c.onHover else c.secondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(NoctaliaStyle.spaceMd))
        Text(text, color = if (selected) c.onHover else c.onSurface, fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(NoctaliaStyle.spaceSm))
        Text(kind, color = if (selected) c.onHover else c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
    }
}

/** A contact: initial avatar and name. Tap opens the contact; long-press offers Call / Message. */
@Composable
private fun ContactRow(result: Scored<ContactEntry>, selected: Boolean, repo: ContactsRepository, onClick: () -> Unit) {
    val c = Noctalia.colors
    val contact = result.item
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Box {
        ResultRowFrame(selected, onClick, onLongClick = if (contact.hasPhone) ({ menu = true }) else null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (selected) c.onHover.copy(alpha = 0.15f) else c.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(contact.name.first().uppercase(), color = if (selected) c.onHover else c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(NoctaliaStyle.spaceMd))
            Text(highlight(contact.name, result.matched, if (selected) c.onHover else c.primary), color = if (selected) c.onHover else c.onSurface,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(NoctaliaStyle.spaceSm))
            Text("Contact", color = if (selected) c.onHover else c.onSurfaceVariant, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = c.surfaceContainer,
            shape = RoundedCornerShape(Noctalia.style.radiusLg),
        ) {
            DropdownMenuItem(
                text = { Text("Call", color = c.onSurface) },
                leadingIcon = { Icon(NIcons.Phone, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; scope.launch { repo.phoneNumber(contact)?.let(repo::dial) } },
            )
            DropdownMenuItem(
                text = { Text("Message", color = c.onSurface) },
                leadingIcon = { Icon(NIcons.Message, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; scope.launch { repo.phoneNumber(contact)?.let(repo::message) } },
            )
            DropdownMenuItem(
                text = { Text("Open contact", color = c.onSurface) },
                leadingIcon = { Icon(NIcons.User, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; repo.open(contact) },
            )
        }
    }
}

/** An app shortcut: its icon and label, with the app it belongs to on the right. */
@Composable
private fun ShortcutRow(result: ShortcutResult, selected: Boolean, shortcutIcon: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    val c = Noctalia.colors
    val shortcut = result.shortcut
    val bitmap by produceState<ImageBitmap?>(null, shortcut.id, shortcutIcon) { value = shortcutIcon(shortcut.id) }
    val fg = if (selected) c.onHover else c.onSurface
    ResultRowFrame(selected, onClick) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(26.dp)) }
                ?: Icon(NIcons.ArrowRight, null, tint = if (selected) c.onHover else c.secondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(NoctaliaStyle.spaceMd))
        Text(
            highlight(shortcut.label, result.matched, if (selected) c.onHover else c.primary),
            color = fg,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(NoctaliaStyle.spaceSm))
        Text(result.appLabel, color = if (selected) c.onHover else c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
    }
}

@Composable
private fun EmojiRow(result: Scored<EmojiEntry>, selected: Boolean, onClick: () -> Unit) {
    val c = Noctalia.colors
    ResultRowFrame(selected, onClick) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(result.item.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(NoctaliaStyle.spaceMd))
        Text(highlight(result.item.name, result.matched, if (selected) c.onHover else c.primary), color = if (selected) c.onHover else c.onSurface,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Text("Enter to copy", color = c.onHover, fontSize = 12.sp)
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
