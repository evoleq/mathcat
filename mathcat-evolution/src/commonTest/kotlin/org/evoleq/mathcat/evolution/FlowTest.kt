package org.evoleq.mathcat.evolution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private sealed interface AppEvent {
    data object Start : AppEvent
    data class Navigate(val path: String) : AppEvent
    data class Loaded(val path: String) : AppEvent
    data object Stop : AppEvent
}

private data class AppState(val page: String = "", val running: Boolean = true)
private sealed interface Command {
    data object ReadEnvironment : Command
    data class Match(val path: String) : Command
}

class FlowTest {
    @Test
    fun typedBranchesDescribeEffectsWithoutRunningThem() {
        val navigation = Flow<AppState, AppEvent, Command> {
            on<AppEvent.Navigate> { state, event ->
                Evolve(state.copy(page = event.path), Command.Match(event.path))
            }
        }
        val app = Flow<AppState, AppEvent, Command> {
            on<AppEvent.Start> { state, _ -> Evolve(state, Command.ReadEnvironment) }
            include(navigation)
            on<AppEvent.Stop> { state, _ -> Evolve(state.copy(running = false)) }
        }
        assertEquals(listOf(Command.ReadEnvironment), app.evolve(AppState(), AppEvent.Start).commands)
        val navigated = app.evolve(AppState(), AppEvent.Navigate("/app/shares/42"))
        assertEquals("/app/shares/42", navigated.state.page)
        assertEquals(listOf(Command.Match("/app/shares/42")), navigated.commands)
        assertEquals(AppState(), app.evolve(AppState(), AppEvent.Loaded("old")).state)
        assertEquals(false, app.evolve(AppState(), AppEvent.Stop).state.running)
    }

    @Test
    fun orderAndFallbackAreExplicit() {
        val flow = Flow<Int, String, String> {
            on({ it.startsWith("a") }) { state, _ -> Evolve(state + 1, "first", "second") }
            on({ it == "alpha" }) { state, _ -> Evolve(state + 100, "shadowed") }
            otherwise { state, _ -> Evolve(state, "ignored") }
        }
        assertEquals(Evolution(2, listOf("first", "second")), flow.evolve(1, "alpha"))
        assertEquals(Evolution(1, listOf("ignored")), flow.evolve(1, "beta"))
    }

    @Test
    fun stateAndCommandsAreDeterministicForTheSameInputs() {
        val flow = Flow<Int, Int, String> {
            on({ it > 0 }) { state, event -> Evolve(state + event, "persist", "render") }
        }
        assertEquals(flow.evolve(3, 7), flow.evolve(3, 7))
        assertEquals(Evolution(3, emptyList()), flow.evolve(3, -1))
    }

    @Test
    fun fragmentsCannotSmuggleAnAlternativeDefault() {
        val fragment = Flow<Int, String, String> {
            otherwise { s, _ -> Evolve(s) }
        }
        assertFailsWith<IllegalArgumentException> {
            Flow<Int, String, String> { include(fragment) }
        }
    }
}
