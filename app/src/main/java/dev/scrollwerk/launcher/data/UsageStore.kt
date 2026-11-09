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

/** A launchable activity. Pure data so search and ranking are unit-testable. */
data class AppEntry(
    val id: String,
    val label: String,
    val packageName: String,
    /** Android's app category ("Games", "Social", ...), or null when the app declares none. */
    val category: String? = null,
)

/** State the launcher learns as you use it (not config): launch counts, recents, pins. */
@Serializable
data class UsageState(
    val counts: Map<String, Int> = emptyMap(),
    val recent: List<String> = emptyList(),
    val pinned: List<String> = emptyList(),
) {
    fun count(id: String): Int = counts[id] ?: 0

    fun launched(id: String) = copy(
        counts = counts + (id to count(id) + 1),
        recent = (listOf(id) + recent.filter { it != id }).take(MAX_RECENT),
    )

    fun togglePin(id: String) = copy(pinned = if (id in pinned) pinned - id else pinned + id)

    companion object {
        const val MAX_RECENT = 20
    }
}

class UsageStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UsageState> = _state

    fun update(transform: (UsageState) -> UsageState) {
        _state.update(transform)
        scope.launch {
            writeLock.withLock {
                runCatching { file.writeText(json.encodeToString(UsageState.serializer(), _state.value)) }
            }
        }
    }

    private fun load(): UsageState = runCatching {
        json.decodeFromString(UsageState.serializer(), file.readText())
    }.getOrDefault(UsageState())
}
