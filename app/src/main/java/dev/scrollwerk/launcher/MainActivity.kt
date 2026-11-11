package dev.scrollwerk.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import dev.scrollwerk.launcher.ui.theme.Palettes
import dev.scrollwerk.launcher.ui.theme.WallpaperGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.config.ConfigStore
import dev.scrollwerk.launcher.config.watchFiles
import dev.scrollwerk.launcher.ui.theme.PaletteImport
import dev.scrollwerk.launcher.ui.theme.ThemeFonts
import dev.scrollwerk.launcher.ui.theme.UserFontFiles
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.config.LoadedConfig
import dev.scrollwerk.launcher.config.ThemeMode
import dev.scrollwerk.launcher.data.AppRepository
import dev.scrollwerk.launcher.data.UsageStore
import dev.scrollwerk.launcher.data.WorkspaceStore
import dev.scrollwerk.launcher.data.WsColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import dev.scrollwerk.launcher.ui.HomeScreen
import dev.scrollwerk.launcher.ui.Overlay
import dev.scrollwerk.launcher.ui.theme.NoctaliaTheme
import dev.scrollwerk.launcher.widgets.WeatherRepository
import dev.scrollwerk.launcher.notifications.NotificationFeed
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private lateinit var configStore: ConfigStore
    private lateinit var usageStore: UsageStore
    private lateinit var weather: WeatherRepository
    private lateinit var workspaceStore: WorkspaceStore
    private var homePresses by mutableIntStateOf(0)
    private var loaded by mutableStateOf(LoadedConfig(LauncherConfig()))
    private var overlay by mutableStateOf(Overlay.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        configStore = ConfigStore(File(getExternalFilesDir(null) ?: filesDir, "config.json"))
        usageStore = UsageStore(File(filesDir, "usage.json"))
        weather = WeatherRepository(applicationContext)
        workspaceStore = WorkspaceStore(File(filesDir, "workspaces.json"))
        loaded = configStore.load()

        setContent {
            val apps by remember { repository.apps() }.collectAsStateWithLifecycle(emptyList())
            val shortcuts by remember { repository.shortcuts() }.collectAsStateWithLifecycle(emptyList())
            val usage by usageStore.state.collectAsStateWithLifecycle()
            val workspaces by workspaceStore.state.collectAsStateWithLifecycle()
            // Forget uninstalled apps (but not before the app list has loaded).
            LaunchedEffect(apps) {
                if (apps.isNotEmpty()) workspaceStore.update { it.prune(apps.mapTo(HashSet()) { a -> a.id }) }
            }
            NoctaliaTheme(loaded.config.theme) {
                HomeScreen(
                    config = loaded.config,
                    configError = listOfNotNull(loaded.error, paletteError, fontError).joinToString("\n").ifEmpty { null },
                    apps = apps,
                    usage = usage,
                    workspaces = workspaces,
                    onWorkspaces = workspaceStore::update,
                    onOpenColumn = ::openColumn,
                    homePresses = homePresses,
                    overlay = overlay,
                    onOverlay = { overlay = it },
                    weather = weather,
                    loadIcon = repository::icon,
                    shortcuts = shortcuts,
                    loadShortcutIcon = repository::shortcutIcon,
                    onLaunchShortcut = { shortcut ->
                        if (repository.launchShortcut(shortcut.id)) usageStore.update { it.launched(shortcut.appId) }
                        overlay = Overlay.None
                    },
                    onLaunch = { app ->
                        if (repository.launch(app.id)) usageStore.update { it.launched(app.id) }
                        overlay = Overlay.None
                    },
                    onTogglePin = { app -> usageStore.update { it.togglePin(app.id) } },
                    onAppInfo = { app -> repository.openAppInfo(app.id) },
                    onConfigChange = ::updateConfig,
                    onApplyWallpaper = { applyMatchingWallpaper() },
                )
            }
        }
    }

    /** A one-app column opens its app; a two-app column opens both in split screen. */
    private fun openColumn(column: WsColumn) {
        val ok = when (column.apps.size) {
            1 -> repository.launch(column.apps[0])
            2 -> repository.launchSplit(column.apps[0], column.apps[1])
            else -> false
        }
        if (ok) usageStore.update { u -> column.apps.fold(u) { acc, id -> acc.launched(id) } }
    }

    /** In-app settings (theme, calendar app) write config.json, so the file stays the single source of truth. */
    private fun updateConfig(edit: (LauncherConfig) -> LauncherConfig) {
        val before = loaded.config.theme
        val updated = edit(loaded.config)
        val after = updated.theme
        val themeChanged = before.palette != after.palette || before.mode != after.mode
        val justEnabled = !before.matchingWallpaper && after.matchingWallpaper
        val lockJustEnabled = !before.matchingLockScreen && after.matchingLockScreen
        when {
            after.matchingWallpaper && (themeChanged || justEnabled) -> applyMatchingWallpaper(after)
            lockJustEnabled -> applyMatchingWallpaper(after, home = false, lock = true) // show what the switch did
        }
        configStore.save(updated)
        loaded = LoadedConfig(updated)
        NotificationFeed.configure(this, updated.notifications)
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        reloadConfig()
        reloadPalettes()
        reloadFonts()
    }

    private var configWatch: AutoCloseable? = null
    private var paletteWatch: AutoCloseable? = null
    private var paletteError by mutableStateOf<String?>(null)
    private val paletteDir by lazy { File(getExternalFilesDir(null) ?: filesDir, "palettes") }
    private val fontDir by lazy { File(getExternalFilesDir(null) ?: filesDir, "fonts") }
    private var fontWatch: AutoCloseable? = null
    private var fontError by mutableStateOf<String?>(null)

    /** Re-reads fonts/ (off the main thread); the Font chooser and the theme pick them up by themselves. */
    private fun reloadFonts() {
        lifecycleScope.launch {
            val (fonts, errors) = withContext(Dispatchers.IO) { fontDir.mkdirs(); UserFontFiles.loadFolder(fontDir) }
            ThemeFonts.user = fonts
            fontError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
    }

    /** Re-reads palettes/ (off the main thread); a palette the config names re-resolves by itself. */
    private fun reloadPalettes() {
        lifecycleScope.launch {
            val (palettes, errors) = withContext(Dispatchers.IO) { paletteDir.mkdirs(); PaletteImport.loadFolder(paletteDir) }
            Palettes.user = palettes
            paletteError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
    }

    /** While visible, edits to config.json apply right away instead of on the next resume. */
    override fun onStart() {
        super.onStart()
        configWatch = configStore.watch(::reloadConfig)
        paletteWatch = watchFiles(paletteDir, PaletteImport::accepts, ::reloadPalettes)
        fontWatch = watchFiles(fontDir, UserFontFiles::accepts, ::reloadFonts)
    }

    override fun onStop() {
        configWatch?.close()
        configWatch = null
        paletteWatch?.close()
        paletteWatch = null
        fontWatch?.close()
        fontWatch = null
        super.onStop()
    }

    private fun reloadConfig() {
        loaded = configStore.load(lastGood = loaded.config)
        NotificationFeed.configure(this, loaded.config.notifications) // also picks up a new default SMS app
        applySystemBars()
    }

    /**
     * Home pressed while already on the home screen: close what's open, or go to the desktop
     * page. Home pressed from another app only brings us back, keeping the current workspace.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        if (!hasWindowFocus()) return
        if (overlay != Overlay.None) overlay = Overlay.None else homePresses++
    }

    private fun isDark(mode: ThemeMode) = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Renders the palette's wallpaper at screen size and sets it as the home-screen wallpaper. */
    /** Sets the palette's home-screen wallpaper and, with [lock], its lock-screen version. */
    private fun applyMatchingWallpaper(
        theme: LauncherConfig.Theme = loaded.config.theme,
        home: Boolean = true,
        lock: Boolean = theme.matchingLockScreen,
    ) {
        val palette = Palettes.byName(theme.palette) ?: return // "wallpaper" palette: nothing to match
        val colors = if (isDark(theme.mode)) palette.dark else palette.light
        val bounds = windowManager.maximumWindowMetrics.bounds
        val seed = WallpaperGenerator.seedFor(palette.name)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val manager = WallpaperManager.getInstance(applicationContext)
                fun set(bitmap: android.graphics.Bitmap, which: Int) =
                    runCatching { manager.setBitmap(bitmap, null, true, which) }.isSuccess.also { bitmap.recycle() }
                val homeOk = !home || set(WallpaperGenerator.render(colors, bounds.width(), bounds.height(), seed), WallpaperManager.FLAG_SYSTEM)
                val lockOk = !lock || set(WallpaperGenerator.renderLock(colors, bounds.width(), bounds.height(), seed), WallpaperManager.FLAG_LOCK)
                homeOk && lockOk
            }
            if (!ok) Toast.makeText(this@MainActivity, "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySystemBars() {
        val config = loaded.config
        val dark = isDark(config.theme.mode)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (config.bar.hideSystemStatusBar) hide(WindowInsetsCompat.Type.statusBars())
            else show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
