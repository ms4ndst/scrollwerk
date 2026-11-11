package dev.scrollwerk.launcher.data

import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.data.WorkspacesState.Companion.DRAFT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private var n = 0
    private val ids = { "id${++n}" }
    private val slack = AppEntry("com.Slack/.Home#0", "Slack", "com.Slack")
    private val mail = AppEntry("com.fsck.k9/.Main#0", "K-9 Mail", "com.fsck.k9")

    /** Workspace 1 "Tools" with app a, workspace 2 (unnamed) with app b. */
    private fun state(): WorkspacesState {
        var s = WorkspacesState().addColumn(DRAFT_ID, "a", ids).addColumn(DRAFT_ID, "b", ids)
        return s.rename(s.workspaces[0].id, "Tools")
    }

    @Test fun rulesMatchPackageOrLabelAndLaterWins() {
        val rules = listOf(AppRule("com.slack", "1"), AppRule("Slack", "Chat"))
        assertEquals("Chat", Rules.ruleFor(rules, slack)?.workspace)
        assertNull(Rules.ruleFor(rules, mail))
    }

    @Test fun targetsByNameThenNumberElseNew() {
        val s = state()
        assertEquals(Rules.Target.Existing(s.workspaces[0].id), Rules.target(s, AppRule("x", "tools")))
        assertEquals(Rules.Target.Existing(s.workspaces[1].id), Rules.target(s, AppRule("x", "2")))
        assertEquals(Rules.Target.New("Chat"), Rules.target(s, AppRule("x", "Chat")))
        assertEquals(Rules.Target.New(null), Rules.target(s, AppRule("x", "9")))
    }

    @Test fun placingCreatesANamedWorkspaceWhenNeeded() {
        val (s, wsId) = Rules.place(state(), AppRule("Slack", "Chat"), slack.id, ids)
        val chat = s.workspaces.single { it.id == wsId }
        assertEquals("Chat", chat.name)
        assertEquals(listOf(listOf(slack.id)), chat.columns.map { it.apps })
        assertTrue(Rules.isPlaced(s, slack.id))
        // A second launch finds the workspace by name instead of making another.
        val (s2, again) = Rules.place(s, AppRule("Slack", "Chat"), mail.id, ids)
        assertEquals(wsId, again)
        assertEquals(s.workspaces.size, s2.workspaces.size)
    }

    @Test fun columnMenuTogglesARuleForThatWorkspace() {
        val s = state()
        val tools = s.workspaces[0].id
        val on = Rules.toggle(emptyList(), slack, s, tools)
        assertEquals(listOf(AppRule("com.Slack", "Tools")), on)
        assertTrue(Rules.pointsHere(on, slack, s, tools))
        assertFalse(Rules.pointsHere(on, slack, s, s.workspaces[1].id))
        assertEquals(listOf(AppRule("com.Slack", "2")), Rules.toggle(on, slack, s, s.workspaces[1].id)) // moved, by number
        assertTrue(Rules.toggle(on, slack, s, tools).isEmpty()) // off again
    }

    @Test fun rulesRoundTripThroughConfig() {
        val config = LauncherConfig(rules = listOf(AppRule("com.Slack", "Chat", autoAdd = false)))
        assertEquals(config.rules, LauncherConfig.parse(LauncherConfig.encode(config)).getOrThrow().rules)
        assertTrue(LauncherConfig().rules.isEmpty())
    }
}
