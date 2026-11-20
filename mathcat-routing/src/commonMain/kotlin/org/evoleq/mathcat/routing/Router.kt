package org.evoleq.mathcat.routing

import org.evoleq.mathcat.evolution.Evolution
import org.evoleq.mathcat.evolution.Flow
import org.evoleq.mathcat.types.Sum2

/**
 * A **pure** routing subautomaton. One application-owned Flow may incorporate
 * its transitions; this module starts no job, loads no plugin and owns no Store.
 */
enum class HistoryMode { Push, Replace, None }

data class Navigation(
    val url: String,
    val match: RouteMatch,
    val history: HistoryMode
)

sealed interface RouterPhase {
    data object Idle : RouterPhase
    data class Checking(val generation: Long, val navigation: Navigation) : RouterPhase
    data class Loading(val generation: Long, val navigation: Navigation) : RouterPhase
    data class Active(val generation: Long, val navigation: Navigation) : RouterPhase
    data class Denied(val generation: Long, val navigation: Navigation) : RouterPhase
    data class Failed(val generation: Long, val navigation: Navigation, val reason: String) : RouterPhase
    data class Rejected(val error: RouteError) : RouterPhase
    data object Stopped : RouterPhase
}

data class RouterState(
    val phase: RouterPhase = RouterPhase.Idle,
    val generation: Long = 0L
)

sealed interface RouterEvent {
    /**
     * The address bar and popstate use None: an existing URL must not be
     * pushed into browser history a second time.
     */
    data class Navigate(
        val url: String,
        val history: HistoryMode = HistoryMode.Push
    ) : RouterEvent

    data class AccessResolved(val generation: Long, val granted: Boolean) : RouterEvent
    data class PluginActivated(val generation: Long, val pluginId: String) : RouterEvent
    data class PluginRejected(
        val generation: Long,
        val pluginId: String,
        val reason: String
    ) : RouterEvent
    data object Stop : RouterEvent
}

/** Commands are inert descriptions; the host supplies the interpreters. */
sealed interface RouterCommand {
    data class CheckAccess(
        val generation: Long,
        val match: RouteMatch
    ) : RouterCommand

    data class LoadPlugin(
        val generation: Long,
        val pluginId: String
    ) : RouterCommand

    data class CancelPending(val generation: Long) : RouterCommand

    data class ChangeHistory(
        val url: String,
        val mode: HistoryMode
    ) : RouterCommand
}

/**
 * Constructs the router as an ordinary Evoleq Flow of pure events.
 * A real application runtime owns delivery, cancellation and command IO.
 */
@Suppress("FunctionName")
fun Router(routes: CompiledRoutes): Flow<RouterState, RouterEvent, RouterCommand> =
    Flow {
        on<RouterEvent.Navigate> { state, event ->
            navigate(routes, state, event)
        }
        on<RouterEvent.AccessResolved> { state, event ->
            val phase = state.phase
            if (phase is RouterPhase.Checking && phase.generation == event.generation) {
                if (!event.granted) {
                    Evolution(state.copy(phase = RouterPhase.Denied(phase.generation, phase.navigation)),
                        emptyList())
                } else {
                    afterAuthorization(state, phase.generation, phase.navigation)
                }
            } else unchanged(state)
        }
        on<RouterEvent.PluginActivated> { state, event ->
            val phase = state.phase
            if (phase is RouterPhase.Loading && phase.generation == event.generation &&
                phase.navigation.match.plugin == event.pluginId) {
                activate(state, phase.generation, phase.navigation)
            } else unchanged(state)
        }
        on<RouterEvent.PluginRejected> { state, event ->
            val phase = state.phase
            if (phase is RouterPhase.Loading && phase.generation == event.generation &&
                phase.navigation.match.plugin == event.pluginId) {
                Evolution(
                    state.copy(phase = RouterPhase.Failed(phase.generation, phase.navigation, event.reason)),
                    emptyList()
                )
            } else unchanged(state)
        }
        on<RouterEvent.Stop> { state, _ ->
            if (state.phase is RouterPhase.Stopped) unchanged(state)
            else Evolution(state.copy(phase = RouterPhase.Stopped), cancellation(state.phase))
        }
    }

private fun unchanged(state: RouterState): Evolution<RouterState, RouterCommand> =
    Evolution(state, emptyList())

/** An in-flight generation may be cancelled, but does not own any loaded plugin. */
private fun cancellation(phase: RouterPhase): List<RouterCommand> = when (phase) {
    is RouterPhase.Checking -> listOf(RouterCommand.CancelPending(phase.generation))
    is RouterPhase.Loading -> listOf(RouterCommand.CancelPending(phase.generation))
    else -> emptyList()
}

private fun navigate(
    routes: CompiledRoutes,
    state: RouterState,
    event: RouterEvent.Navigate
): Evolution<RouterState, RouterCommand> {
    if (state.phase is RouterPhase.Stopped) return unchanged(state)
    // Prevent generation IDs from wrapping and matching an old asynchronous completion.
    require(state.generation < Long.MAX_VALUE) { "Navigation generation exhausted" }
    val generation = state.generation + 1L
    val cancelled = cancellation(state.phase)

    return when (val found = routes.match(event.url)) {
        is Sum2.Left -> Evolution(
            state.copy(generation = generation, phase = RouterPhase.Rejected(found.value)),
            cancelled
        )
        is Sum2.Right -> {
            val navigation = Navigation(event.url, found.value, event.history)
            when {
                found.value.requires.isNotEmpty() -> Evolution(
                    state.copy(generation = generation, phase = RouterPhase.Checking(generation, navigation)),
                    cancelled + RouterCommand.CheckAccess(generation, found.value)
                )
                else -> {
                    val fresh = state.copy(generation = generation)
                    val result = afterAuthorization(fresh, generation, navigation)
                    result.copy(commands = cancelled + result.commands)
                }
            }
        }
    }
}

private fun afterAuthorization(
    state: RouterState,
    generation: Long,
    navigation: Navigation
): Evolution<RouterState, RouterCommand> {
    val pluginId = navigation.match.plugin
    return if (pluginId == null) activate(state, generation, navigation)
    else Evolution(
        state.copy(phase = RouterPhase.Loading(generation, navigation)),
        listOf(RouterCommand.LoadPlugin(generation, pluginId))
    )
}

private fun activate(
    state: RouterState,
    generation: Long,
    navigation: Navigation
): Evolution<RouterState, RouterCommand> =
    Evolution(
        state.copy(phase = RouterPhase.Active(generation, navigation)),
        when (navigation.history) {
            HistoryMode.None -> emptyList()
            else -> listOf(RouterCommand.ChangeHistory(navigation.url, navigation.history))
        }
    )
