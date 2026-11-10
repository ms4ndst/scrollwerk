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
import dev.scrollwerk.launcher.data.UsageState
import dev.scrollwerk.launcher.data.Workspace
import dev.scrollwerk.launcher.data.WorkspacesState
import dev.scrollwerk.launcher.data.WsColumn
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import dev.scrollwerk.launcher.widgets.WeatherRepository
import kotlinx.coroutines.launch

enum class Overlay { None, Launcher, ControlCenter }

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
    icon: suspend (String) -> ImageBitmap?,
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

    val actions = WorkspaceActions(
        open = onOpenColumn,
        addColumn = { ws -> pick = PickTarget.NewColumn(ws.id); onOverlay(Overlay.Launcher) },
        split = { ws, col -> pick = PickTarget.SplitWith(ws.id, col.id); onOverlay(Overlay.Launcher) },
        removeFromColumn = { ws, col, app -> editWorkspaces { it.removeFromColumn(ws.id, col.id, app) } },
        move = { ws, col, delta -> editWorkspaces { it.moveColumn(ws.id, col.id, delta) } },
        removeColumn = { ws, col -> editWorkspaces { it.removeColumn(ws.id, col.id) } },
    )

    // The workspace the launcher's "Add to workspace" goes to: the one in view, else the first.
    val currentWorkspace: Workspace = displayed.getOrNull(pager.currentPage - 1) ?: displayed.first()
    val currentNumber = displayed.indexOf(currentWorkspace) + 1

    val appsById = remember(apps) { apps.associateBy { it.id } }
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
                    WorkspaceDots(pager.pageCount, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    WorkspacePage(page, ws, appsById, icon, actions)
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
                    null -> onLaunch(app)
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
                null -> "Search apps, or type 12*7…"
                is PickTarget.NewColumn -> "Pick an app for workspace ${pageOf(picking.workspaceId, workspaces)}"
                is PickTarget.SplitWith -> "Pick the second app for split screen"
                PickTarget.CalendarApp -> "Pick your calendar app"
                PickTarget.EmailApp -> "Pick your email app"
            },
            extraAction = if (picking != null) null else RowAction(
                if (currentWorkspace.id == WorkspacesState.DRAFT_ID) "Add to new workspace" else "Add to workspace $currentNumber",
            ) { app ->
                onOverlay(Overlay.None)
                editWorkspaces(follow = currentWorkspace.id) { it.addColumn(currentWorkspace.id, app.id) }
            },
        )
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
