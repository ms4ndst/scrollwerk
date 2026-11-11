package dev.scrollwerk.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.data.AppEntry
import dev.scrollwerk.launcher.data.IconTint
import dev.scrollwerk.launcher.data.Rules
import dev.scrollwerk.launcher.search.ShortcutEntry
import androidx.compose.ui.graphics.toArgb
import dev.scrollwerk.launcher.data.UsageState
import dev.scrollwerk.launcher.data.Workspace
import dev.scrollwerk.launcher.data.WorkspacesState
import dev.scrollwerk.launcher.data.WsColumn
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import dev.scrollwerk.launcher.widgets.WeatherRepository
import dev.scrollwerk.launcher.notifications.Badge
import dev.scrollwerk.launcher.notifications.NotificationFeed
import dev.scrollwerk.launcher.notifications.badgesByPackage
import dev.scrollwerk.launcher.notifications.columnBadge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

enum class Overlay { None, Launcher, ControlCenter, Overview }

/** What the launcher is choosing an app for, when it's opened from a workspace. */
private sealed interface PickTarget {
    data class NewColumn(val workspaceId: String) : PickTarget
    data class SplitWith(val workspaceId: String, val columnId: String) : PickTarget
    data object CalendarApp : PickTarget
    data object EmailApp : PickTarget
}

/**
 * Pages, top to bottom like niri's workspaces: the widget desktop, then each workspace,
 * then one empty workspace. Bar on top, search pill at the bottom, overlays above all.
 */
@Composable
fun HomeScreen(
    config: LauncherConfig,
    configError: String?,
    apps: List<AppEntry>,
    usage: UsageState,
    workspaces: WorkspacesState,
    onWorkspaces: ((WorkspacesState) -> WorkspacesState) -> Unit,
    onOpenColumn: (WsColumn) -> Unit,
    overlay: Overlay,
    onOverlay: (Overlay) -> Unit,
    homePresses: Int,
    weather: WeatherRepository,
    loadIcon: suspend (String, IconTint?) -> ImageBitmap?,
    shortcuts: List<ShortcutEntry>,
    loadShortcutIcon: suspend (String, IconTint?) -> ImageBitmap?,
    onLaunchShortcut: (ShortcutEntry) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onConfigChange: ((LauncherConfig) -> LauncherConfig) -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    val displayed = workspaces.displayed
    val pager = rememberPagerState { 1 + displayed.size }
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf<PickTarget?>(null) }
    var alertsOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Workspace?>(null) }
    LaunchedEffect(config.notifications.enabled) { if (!config.notifications.enabled) alertsOpen = false }
    LaunchedEffect(overlay) { if (overlay != Overlay.Launcher) pick = null }

    // Home while already home: back to the desktop page.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            alertsOpen = false
            pager.animateScrollToPage(0)
        }
    }

    // Back closes an overlay, then returns to the desktop; it never leaves the home screen.
    BackHandler {
        when {
            overlay != Overlay.None -> onOverlay(Overlay.None)
            alertsOpen -> alertsOpen = false
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    fun pageOf(workspaceId: String, state: WorkspacesState): Int =
        1 + state.displayed.indexOfFirst { it.id == workspaceId }.coerceAtLeast(0)

    /** Applies an edit and keeps the pager on the workspace it was about (it may have been created or removed). */
    fun editWorkspaces(follow: String? = null, edit: (WorkspacesState) -> WorkspacesState) {
        val before = workspaces.workspaces.map { it.id }.toSet()
        val after = edit(workspaces)
        onWorkspaces { after }
        val target = follow?.let { id ->
            if (id == WorkspacesState.DRAFT_ID) after.workspaces.firstOrNull { it.id !in before }?.id else id
        }
        scope.launch {
            if (target != null && after.workspaces.any { it.id == target }) pager.animateScrollToPage(pageOf(target, after))
            else pager.animateScrollToPage(pager.currentPage.coerceAtMost(after.displayed.size))
        }
    }

    val appsById = remember(apps) { apps.associateBy { it.id } }

    val actions = WorkspaceActions(
        open = onOpenColumn,
        addColumn = { ws -> pick = PickTarget.NewColumn(ws.id); onOverlay(Overlay.Launcher) },
        split = { ws, col -> pick = PickTarget.SplitWith(ws.id, col.id); onOverlay(Overlay.Launcher) },
        removeFromColumn = { ws, col, app -> editWorkspaces { it.removeFromColumn(ws.id, col.id, app) } },
        move = { ws, col, delta -> editWorkspaces { it.moveColumn(ws.id, col.id, delta) } },
        removeColumn = { ws, col -> editWorkspaces { it.removeColumn(ws.id, col.id) } },
        rename = { ws -> renaming = ws },
        ruleHere = { ws, col ->
            if (col.isSplit) null else appsById[col.apps[0]]?.let { Rules.pointsHere(config.rules, it, workspaces, ws.id) }
        },
        toggleRule = { ws, col ->
            appsById[col.apps.firstOrNull()]?.let { app ->
                onConfigChange { c -> c.copy(rules = Rules.toggle(c.rules, app, workspaces, ws.id)) }
            }
        },
    )

    // The workspace the launcher's "Add to workspace" goes to: the one in view, else the first.
    val currentWorkspace: Workspace = displayed.getOrNull(pager.currentPage - 1) ?: displayed.first()
    val currentNumber = displayed.indexOf(currentWorkspace) + 1


    // Themed icons follow the palette; a new lambda makes every icon reload in the new colours.
    val colors = Noctalia.colors
    val icon: suspend (String) -> ImageBitmap? = remember(loadIcon, config.theme.themedIcons, colors) {
        val tint = if (config.theme.themedIcons) IconTint(colors.primary.toArgb(), colors.surfaceContainer.toArgb()) else null
        val load: suspend (String) -> ImageBitmap? = { id -> loadIcon(id, tint) }
        load
    }
    val shortcutIcon: suspend (String) -> ImageBitmap? = remember(loadShortcutIcon, config.theme.themedIcons, colors) {
        val tint = if (config.theme.themedIcons) IconTint(colors.primary.toArgb(), colors.surfaceContainer.toArgb()) else null
        val load: suspend (String) -> ImageBitmap? = { id -> loadShortcutIcon(id, tint) }
        load
    }

    // Alert counts on workspace columns, from the notification bar's sources.
    val alerts by NotificationFeed.alerts.collectAsStateWithLifecycle()
    val badges = remember(alerts, config.notifications) {
        if (!config.notifications.enabled) emptyMap()
        else badgesByPackage(alerts.filter { it.source.enabledIn(config.notifications) }.map { it.packageName to it.source })
    }
    val columnBadge: (WsColumn) -> Badge? = { col -> columnBadge(col.apps.mapNotNull { appsById[it]?.packageName }, badges) }
    val setOverlay by rememberUpdatedState(onOverlay)
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val pullDown = remember(thresholdPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > thresholdPx) { pulled = 0f; setOverlay(Overlay.ControlCenter) }
                }
                return Offset.Zero // the pager still gets the rest
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Bar(
                config.bar,
                onLauncherClick = { onOverlay(Overlay.Launcher) },
                onControlCenterClick = { onOverlay(Overlay.ControlCenter) },
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
                workspaceDots = {
                    WorkspaceDots(
                        pager.pageCount, pager.currentPage,
                        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                        onLongPress = { onOverlay(Overlay.Overview) },
                    )
                },
            )
            if (config.notifications.enabled) {
                NotificationBar(
                    config.notifications,
                    floating = config.bar.floating,
                    expanded = alertsOpen,
                    onExpandedChange = { alertsOpen = it },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                )
            }
            configError?.let { ConfigErrorBanner(it) }
            VerticalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { page -> if (page == 0) "desktop" else displayed.getOrNull(page - 1)?.id ?: page },
                modifier = Modifier.weight(1f).fillMaxWidth().onPinchIn { setOverlay(Overlay.Overview) },
            ) { page ->
                if (page == 0) {
                    Box(Modifier.fillMaxSize().nestedScroll(pullDown).verticalScroll(rememberScrollState())) {
                        Column {
                            WidgetColumn(
                                config.widgets,
                                config.bar.use24Hour,
                                weather,
                                onCalendarAppChange = { pkg -> onConfigChange { it.copy(widgets = it.widgets.copy(calendarApp = pkg)) } },
                                onPickOtherCalendarApp = { pick = PickTarget.CalendarApp; onOverlay(Overlay.Launcher) },
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                } else {
                    val ws = displayed[page - 1]
                    WorkspacePage(page, ws, appsById, icon, actions, columnBadge)
                }
            }
            SearchPill(
                onClick = { onOverlay(Overlay.Launcher) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }

        val picking = pick
        LauncherPanel(
            visible = overlay == Overlay.Launcher,
            apps = apps,
            usage = usage,
            config = config.launcher,
            icon = icon,
            onLaunch = { app ->
                when (picking) {
                    null -> {
                        // niri's open-on-workspace: an app with a rule joins its workspace the first time it's launched.
                        Rules.ruleFor(config.rules, app)?.takeIf { it.autoAdd && !Rules.isPlaced(workspaces, app.id) }?.let { rule ->
                            onWorkspaces { s -> if (Rules.isPlaced(s, app.id)) s else Rules.place(s, rule, app.id).first }
                        }
                        onLaunch(app)
                    }
                    is PickTarget.NewColumn -> {
                        onOverlay(Overlay.None)
                        editWorkspaces(follow = picking.workspaceId) { it.addColumn(picking.workspaceId, app.id) }
                    }
                    is PickTarget.SplitWith -> {
                        onOverlay(Overlay.None)
                        editWorkspaces(follow = picking.workspaceId) { it.addToColumn(picking.workspaceId, picking.columnId, app.id) }
                    }
                    PickTarget.CalendarApp -> {
                        onOverlay(Overlay.None)
                        onConfigChange { it.copy(widgets = it.widgets.copy(calendarApp = app.packageName)) }
                    }
                    PickTarget.EmailApp -> {
                        onOverlay(Overlay.None)
                        onConfigChange { it.copy(notifications = it.notifications.copy(emailApp = app.packageName)) }
                    }
                }
            },
            onTogglePin = onTogglePin,
            onAppInfo = onAppInfo,
            onDismiss = { onOverlay(Overlay.None) },
            placeholder = when (picking) {
                null -> "Search apps, settings, contacts…"
                is PickTarget.NewColumn -> "Pick an app for workspace ${pageOf(picking.workspaceId, workspaces)}"
                is PickTarget.SplitWith -> "Pick the second app for split screen"
                PickTarget.CalendarApp -> "Pick your calendar app"
                PickTarget.EmailApp -> "Pick your email app"
            },
            extraAction = if (picking != null) null else RowAction(
                label = { app ->
                    Rules.ruleFor(config.rules, app)?.let { "Add to ${Rules.label(it)}" }
                        ?: if (currentWorkspace.id == WorkspacesState.DRAFT_ID) "Add to new workspace" else "Add to workspace $currentNumber"
                },
            ) { app ->
                onOverlay(Overlay.None)
                val rule = Rules.ruleFor(config.rules, app)
                if (rule != null) {
                    val (after, workspaceId) = Rules.place(workspaces, rule, app.id)
                    editWorkspaces(follow = workspaceId) { after }
                } else {
                    editWorkspaces(follow = currentWorkspace.id) { it.addColumn(currentWorkspace.id, app.id) }
                }
            },
            // Not while picking an app for a workspace or the calendar / email setting.
            shortcuts = if (picking == null && config.launcher.shortcuts) shortcuts else emptyList(),
            shortcutIcon = shortcutIcon,
            onLaunchShortcut = onLaunchShortcut,
            providers = picking == null,
            onDisableContacts = { onConfigChange { it.copy(launcher = it.launcher.copy(contacts = false)) } },
        )
        Overview(
            visible = overlay == Overlay.Overview,
            workspaces = workspaces,
            currentPage = pager.currentPage,
            appsById = appsById,
            icon = icon,
            badge = columnBadge,
            onGoToPage = { page ->
                onOverlay(Overlay.None)
                scope.launch { pager.animateScrollToPage(page) }
            },
            onEdit = { edit -> onWorkspaces(edit) },
            onRename = { ws -> renaming = ws },
            onDismiss = { onOverlay(Overlay.None) },
        )
        renaming?.let { ws ->
            RenameWorkspaceDialog(
                current = ws.name,
                number = pageOf(ws.id, workspaces),
                onSave = { name -> onWorkspaces { it.rename(ws.id, name) }; renaming = null },
                onDismiss = { renaming = null },
            )
        }
        ControlCenter(
            visible = overlay == Overlay.ControlCenter,
            theme = config.theme,
            onThemeChange = { theme -> onConfigChange { it.copy(theme = theme) } },
            onApplyWallpaper = onApplyWallpaper,
            widgets = config.widgets,
            onWidgetsChange = { w -> onConfigChange { it.copy(widgets = w) } },
            notifications = config.notifications,
            onNotificationsChange = { n -> onConfigChange { it.copy(notifications = n) } },
            onPickOtherEmailApp = { pick = PickTarget.EmailApp; onOverlay(Overlay.Launcher) },
            onDismiss = { onOverlay(Overlay.None) },
        )
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Noctalia.colors
    Row(
        modifier
            .widthIn(min = 220.dp)
            .height(NoctaliaStyle.controlHeightLg)
            .clip(CircleShape)
            .background(c.surface.copy(alpha = 0.94f))
            .border(NoctaliaStyle.borderWidth, c.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = NoctaliaStyle.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
    ) {
        Icon(NIcons.Search, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text("Search apps…", color = c.onSurfaceVariant, fontSize = 15.sp)
    }
}

@Composable
private fun ConfigErrorBanner(message: String) {
    val style = Noctalia.style
    Text(
        message,
        color = style.colors.onError,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(style.radiusLg))
            .background(style.colors.error)
            .padding(NoctaliaStyle.spaceMd),
    )
}
