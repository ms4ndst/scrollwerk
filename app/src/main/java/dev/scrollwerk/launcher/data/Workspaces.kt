package dev.scrollwerk.launcher.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * niri's layout model, mapped onto what an Android launcher can do:
 * - a workspace is a horizontal strip of columns;
 * - a column holds one app, or two apps that open together in split screen
 *   (niri's column with two stacked windows).
 */
@Serializable
data class WsColumn(val id: String, val apps: List<String>) {
    val isSplit get() = apps.size == 2
}

/** [name] is the user's label ("Work", "Chat"), like niri's named workspaces; null shows "Workspace N". */
@Serializable
data class Workspace(val id: String, val columns: List<WsColumn> = emptyList(), val name: String? = null)

/**
 * Like niri, only non-empty workspaces are stored; the UI always shows one extra empty
 * workspace at the end ([DRAFT_ID]), which becomes real when an app is added to it, and a
 * workspace disappears when its last column is removed.
 */
@Serializable
data class WorkspacesState(val workspaces: List<Workspace> = emptyList()) {

    /** Stored workspaces plus the trailing empty one. */
    val displayed: List<Workspace> get() = workspaces + Workspace(DRAFT_ID)

    fun addColumn(workspaceId: String, appId: String, newId: () -> String = ::randomId): WorkspacesState {
        val column = WsColumn(newId(), listOf(appId))
        return if (workspaceId == DRAFT_ID || workspaces.none { it.id == workspaceId }) {
            copy(workspaces = workspaces + Workspace(newId(), listOf(column)))
        } else {
            edit(workspaceId) { it.copy(columns = it.columns + column) }
        }
    }

    /** Makes a one-app column a split pair. No-op if the column is already full. */
    fun addToColumn(workspaceId: String, columnId: String, appId: String) = editColumn(workspaceId, columnId) {
        if (it.apps.size >= 2 || appId in it.apps) it else it.copy(apps = it.apps + appId)
    }

    fun removeFromColumn(workspaceId: String, columnId: String, appId: String) =
        editColumn(workspaceId, columnId) { it.copy(apps = it.apps - appId) }

    fun removeColumn(workspaceId: String, columnId: String) =
        edit(workspaceId) { ws -> ws.copy(columns = ws.columns.filterNot { it.id == columnId }) }

    /** Moves a column left (-1) or right (+1) within its workspace, like niri's move-column. */
    fun moveColumn(workspaceId: String, columnId: String, delta: Int) = edit(workspaceId) { ws ->
        val from = ws.columns.indexOfFirst { it.id == columnId }
        val to = from + delta
        if (from < 0 || to !in ws.columns.indices) ws
        else ws.copy(columns = ws.columns.toMutableList().apply { add(to, removeAt(from)) })
    }

    /**
     * Moves a column to [toWorkspaceId] at [index] (counted without the moved column, clamped), as
     * the Overview's drag and drop does. [DRAFT_ID] makes a new workspace; a workspace left
     * empty disappears.
     */
    fun moveColumnTo(
        fromWorkspaceId: String,
        columnId: String,
        toWorkspaceId: String,
        index: Int,
        newId: () -> String = ::randomId,
    ): WorkspacesState {
        val column = workspaces.firstOrNull { it.id == fromWorkspaceId }?.columns?.firstOrNull { it.id == columnId } ?: return this
        val without = workspaces.map { ws -> if (ws.id == fromWorkspaceId) ws.copy(columns = ws.columns - column) else ws }
        val moved = if (toWorkspaceId == DRAFT_ID || without.none { it.id == toWorkspaceId }) {
            without + Workspace(newId(), listOf(column))
        } else without.map { ws ->
            if (ws.id != toWorkspaceId) ws
            else ws.copy(columns = ws.columns.toMutableList().apply { add(index.coerceIn(0, size), column) })
        }
        return copy(workspaces = moved).normalized()
    }

    /** Names a workspace; a blank name removes it. Names are trimmed and capped at [MAX_NAME] characters. */
    fun rename(workspaceId: String, name: String?): WorkspacesState {
        val clean = name?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }
        return copy(workspaces = workspaces.map { if (it.id == workspaceId) it.copy(name = clean) else it })
    }

    /** Moves a workspace up (-1) or down (+1), like niri's move-workspace. */
    fun moveWorkspace(workspaceId: String, delta: Int): WorkspacesState {
        val from = workspaces.indexOfFirst { it.id == workspaceId }
        val to = from + delta
        if (from < 0 || to !in workspaces.indices) return this
        return copy(workspaces = workspaces.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Drops apps that were uninstalled; empties collapse like any other removal. */
    fun prune(installed: Set<String>): WorkspacesState {
        if (workspaces.all { ws -> ws.columns.all { c -> c.apps.all { it in installed } } }) return this
        return copy(workspaces = workspaces.map { ws ->
            ws.copy(columns = ws.columns.map { c -> c.copy(apps = c.apps.filter { it in installed }) })
        }).normalized()
    }

    private fun edit(workspaceId: String, f: (Workspace) -> Workspace) =
        copy(workspaces = workspaces.map { if (it.id == workspaceId) f(it) else it }).normalized()

    private fun editColumn(workspaceId: String, columnId: String, f: (WsColumn) -> WsColumn) = edit(workspaceId) { ws ->
        ws.copy(columns = ws.columns.map { if (it.id == columnId) f(it) else it })
    }

    /** Empty columns and empty workspaces disappear. */
    private fun normalized() = copy(workspaces = workspaces
        .map { ws -> ws.copy(columns = ws.columns.filter { it.apps.isNotEmpty() }) }
        .filter { it.columns.isNotEmpty() })

    companion object {
        const val DRAFT_ID = "draft"
        const val MAX_NAME = 24
        private fun randomId() = UUID.randomUUID().toString().take(8)
    }
}

class WorkspaceStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<WorkspacesState> = _state

    fun update(transform: (WorkspacesState) -> WorkspacesState) {
        val before = _state.value
        _state.update(transform)
        if (_state.value == before) return
        scope.launch {
            writeLock.withLock {
                runCatching { file.writeText(json.encodeToString(WorkspacesState.serializer(), _state.value)) }
            }
        }
    }

    private fun load(): WorkspacesState = runCatching {
        json.decodeFromString(WorkspacesState.serializer(), file.readText())
    }.getOrDefault(WorkspacesState())
}
