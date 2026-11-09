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
}
