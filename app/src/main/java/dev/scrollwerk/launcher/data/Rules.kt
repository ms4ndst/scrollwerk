package dev.scrollwerk.launcher.data

import kotlinx.serialization.Serializable

/**
 * niri's window rules, Android-sized: which workspace an app belongs to.
 * [app] is a package name ("com.Slack") or label ("Slack"); [workspace] is a workspace's name
 * ("Chat") or number ("2"). With [autoAdd], launching the app while it's in no workspace adds it there.
 */
@Serializable
data class AppRule(val app: String, val workspace: String, val autoAdd: Boolean = true)

object Rules {
    /** The rule for [app]; later rules win, like niri's. */
    fun ruleFor(rules: List<AppRule>, app: AppEntry): AppRule? = rules.lastOrNull { r ->
        r.app.equals(app.packageName, ignoreCase = true) || r.app.equals(app.label, ignoreCase = true) || r.app == app.id
    }

    /** What a rule's workspace means right now. */
    sealed interface Target {
        data class Existing(val workspaceId: String) : Target
        /** No such workspace yet: make one, named [name] if the rule used a name. */
        data class New(val name: String?) : Target
    }

    fun target(state: WorkspacesState, rule: AppRule): Target {
        val key = rule.workspace.trim()
        state.workspaces.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { return Target.Existing(it.id) }
        val number = key.toIntOrNull() ?: return Target.New(key.takeIf { it.isNotEmpty() })
        return state.workspaces.getOrNull(number - 1)?.let { Target.Existing(it.id) } ?: Target.New(null)
    }

    /** "Chat", or "workspace 3". */
    fun label(rule: AppRule): String = rule.workspace.trim().let { if (it.toIntOrNull() != null) "workspace $it" else it }

    /** Is [appId] in any workspace? */
    fun isPlaced(state: WorkspacesState, appId: String) = state.workspaces.any { ws -> ws.columns.any { appId in it.apps } }

    /** Adds [appId] as a column in the rule's workspace, creating (and naming) it if needed. Returns the new state and workspace id. */
    fun place(state: WorkspacesState, rule: AppRule, appId: String, newId: (() -> String)? = null): Pair<WorkspacesState, String> =
        when (val t = target(state, rule)) {
            is Target.Existing -> (if (newId != null) state.addColumn(t.workspaceId, appId, newId) else state.addColumn(t.workspaceId, appId)) to t.workspaceId
            is Target.New -> {
                val added = if (newId != null) state.addColumn(WorkspacesState.DRAFT_ID, appId, newId) else state.addColumn(WorkspacesState.DRAFT_ID, appId)
                val ws = added.workspaces.last()
                (if (t.name != null) added.rename(ws.id, t.name) else added) to ws.id
            }
        }

    /** The rule key for a workspace: its name, else its number. */
    fun keyFor(workspace: Workspace, number: Int) = workspace.name ?: number.toString()

    /** Does [app] have a rule pointing at this workspace? */
    fun pointsHere(rules: List<AppRule>, app: AppEntry, state: WorkspacesState, workspaceId: String): Boolean =
        ruleFor(rules, app)?.let { (target(state, it) as? Target.Existing)?.workspaceId == workspaceId } == true

    /** Sets [app]'s rule to this workspace, or removes its rules if it already points here. */
    fun toggle(rules: List<AppRule>, app: AppEntry, state: WorkspacesState, workspaceId: String): List<AppRule> {
        val mine = { r: AppRule -> r.app.equals(app.packageName, true) || r.app.equals(app.label, true) || r.app == app.id }
        if (pointsHere(rules, app, state, workspaceId)) return rules.filterNot(mine)
        val index = state.workspaces.indexOfFirst { it.id == workspaceId }
        if (index < 0) return rules
        return rules.filterNot(mine) + AppRule(app.packageName, keyFor(state.workspaces[index], index + 1))
    }
}
