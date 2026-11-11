package dev.scrollwerk.launcher.data

import dev.scrollwerk.launcher.data.WorkspacesState.Companion.DRAFT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesTest {
    private var n = 0
    private val ids = { "id${++n}" }

    private fun WorkspacesState.apps(ws: Int) = workspaces[ws].columns.map { it.apps }

    @Test fun alwaysShowsOneEmptyWorkspaceAtTheEnd() {
        assertEquals(listOf(DRAFT_ID), WorkspacesState().displayed.map { it.id })
        val s = WorkspacesState().addColumn(DRAFT_ID, "a", ids)
        assertEquals(2, s.displayed.size)
        assertTrue(s.displayed.last().columns.isEmpty())
    }

    @Test fun addingToTheEmptyWorkspaceCreatesARealOne() {
        val s = WorkspacesState().addColumn(DRAFT_ID, "a", ids).addColumn(DRAFT_ID, "b", ids)
        assertEquals(2, s.workspaces.size)
        val ws1 = s.workspaces[0].id
        val s2 = s.addColumn(ws1, "c", ids)
        assertEquals(listOf(listOf("a"), listOf("c")), s2.apps(0))
    }

    @Test fun splitColumnsHoldAtMostTwoDistinctApps() {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids)
        val ws = s.workspaces[0].id
        val col = s.workspaces[0].columns[0].id
        s = s.addToColumn(ws, col, "a") // duplicate ignored
        assertEquals(listOf(listOf("a")), s.apps(0))
        s = s.addToColumn(ws, col, "b").addToColumn(ws, col, "c")
        assertEquals(listOf(listOf("a", "b")), s.apps(0))
        assertTrue(s.workspaces[0].columns[0].isSplit)
    }

    @Test fun moveColumnReordersAndClampsAtEdges() {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids)
        val ws = s.workspaces[0].id
        s = s.addColumn(ws, "b", ids).addColumn(ws, "c", ids)
        val a = s.workspaces[0].columns[0].id
        s = s.moveColumn(ws, a, +1)
        assertEquals(listOf("b", "a", "c"), s.apps(0).map { it.single() })
        val unchanged = s.moveColumn(ws, s.workspaces[0].columns[0].id, -1)
        assertEquals(s, unchanged)
    }

    @Test fun removingTheLastColumnRemovesTheWorkspace() {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids).addColumn(DRAFT_ID, "b", ids)
        val first = s.workspaces[0]
        s = s.removeColumn(first.id, first.columns[0].id)
        assertEquals(1, s.workspaces.size)
        assertEquals(listOf(listOf("b")), s.apps(0))
    }

    @Test fun removingOneHalfOfASplitLeavesASingleColumn() {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids)
        val ws = s.workspaces[0].id
        val col = s.workspaces[0].columns[0].id
        s = s.addToColumn(ws, col, "b").removeFromColumn(ws, col, "a")
        assertEquals(listOf(listOf("b")), s.apps(0))
    }

    @Test fun pruneDropsUninstalledAppsAndCollapsesEmpties() {
        var s = WorkspacesState().addColumn(DRAFT_ID, "gone", ids).addColumn(DRAFT_ID, "a", ids)
        val ws2 = s.workspaces[1]
        s = s.addToColumn(ws2.id, ws2.columns[0].id, "gone2")
        val pruned = s.prune(setOf("a"))
        assertEquals(1, pruned.workspaces.size)
        assertEquals(listOf(listOf("a")), pruned.apps(0))
        assertSame(pruned, pruned.prune(setOf("a"))) // nothing to do → same instance, no write
    }

    /** Two workspaces: [a, b, c] and [d]. */
    private fun twoWorkspaces(): WorkspacesState {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids)
        val ws1 = s.workspaces[0].id
        s = s.addColumn(ws1, "b", ids).addColumn(ws1, "c", ids).addColumn(DRAFT_ID, "d", ids)
        return s
    }

    private fun WorkspacesState.col(ws: Int, app: String) = workspaces[ws].columns.first { app in it.apps }.id

    @Test fun overviewMovesAColumnWithinItsWorkspace() {
        val s = twoWorkspaces()
        val ws1 = s.workspaces[0].id
        val moved = s.moveColumnTo(ws1, s.col(0, "c"), ws1, 0)
        assertEquals(listOf(listOf("c"), listOf("a"), listOf("b")), moved.apps(0))
    }

    @Test fun overviewMovesAColumnToAnotherWorkspace() {
        val s = twoWorkspaces()
        val moved = s.moveColumnTo(s.workspaces[0].id, s.col(0, "a"), s.workspaces[1].id, 99) // clamped to the end
        assertEquals(listOf(listOf("b"), listOf("c")), moved.apps(0))
        assertEquals(listOf(listOf("d"), listOf("a")), moved.apps(1))
    }

    @Test fun droppingOnTheEmptyWorkspaceCreatesOneAndEmptiedWorkspacesDisappear() {
        val s = twoWorkspaces()
        val moved = s.moveColumnTo(s.workspaces[1].id, s.col(1, "d"), DRAFT_ID, 0, ids)
        assertEquals(2, moved.workspaces.size) // [a,b,c] and a new [d]; the old [d] is gone
        assertEquals(listOf(listOf("d")), moved.apps(1))
        assertTrue(moved.workspaces[1].id != s.workspaces[1].id)
    }

    @Test fun unknownColumnIsANoOp() {
        val s = twoWorkspaces()
        assertSame(s, s.moveColumnTo(s.workspaces[0].id, "nope", s.workspaces[1].id, 0))
    }

    @Test fun workspacesReorder() {
        val s = twoWorkspaces()
        val moved = s.moveWorkspace(s.workspaces[1].id, -1)
        assertEquals(listOf(s.workspaces[1].id, s.workspaces[0].id), moved.workspaces.map { it.id })
        assertSame(moved, moved.moveWorkspace(moved.workspaces[0].id, -1))
    }

    @Test fun workspacesCanBeNamedAndUnnamed() {
        val s = twoWorkspaces()
        val id = s.workspaces[1].id
        val named = s.rename(id, "  Chat  ")
        assertEquals("Chat", named.workspaces[1].name)
        assertEquals(null, named.workspaces[0].name)
        assertEquals(null, named.rename(id, "   ").workspaces[1].name)
        assertEquals(WorkspacesState.MAX_NAME, named.rename(id, "x".repeat(100)).workspaces[1].name!!.length)
    }

    @Test fun namesSurviveMovesAndOldFilesLoad() {
        val s = twoWorkspaces().let { it.rename(it.workspaces[0].id, "Tools") }
        val moved = s.moveWorkspace(s.workspaces[0].id, +1)
        assertEquals("Tools", moved.workspaces[1].name)
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(WorkspacesState.serializer(), """{"workspaces":[{"id":"w","columns":[{"id":"c","apps":["a"]}]}]}""")
        assertEquals(null, old.workspaces[0].name)
    }
}
