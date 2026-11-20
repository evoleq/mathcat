package org.evoleq.mathcat.evolution.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.evoleq.mathcat.evolution.Evolve
import org.evoleq.mathcat.evolution.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Every test is an example of a lifecycle invariant, not a timing benchmark. */
class FlowRuntimeTest {
    private sealed interface Event {
        data object Start : Event
        data object Completed : Event
        data object Failed : Event
        data class Add(val amount: Int) : Event
    }
    private sealed interface Command {
        data object Load : Command
        data object Slow : Command
        data object First : Command
        data object Second : Command
    }

    @Test fun typedCompletionEventsComeBackThroughTheSameSerializedReducer() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1, Command.Load) }
            on<Event.Completed> { state, _ -> Evolve(state + 10) }
        }
        val runtime = Run(application, 0, backgroundScope) { command, report ->
            assertEquals(Command.Load, command)
            report(Event.Completed)
        }
        runtime.send(Event.Start)
        runCurrent()
        assertEquals(11, runtime.state.value)
        runtime.stop()
    }

    @Test fun eventTransitionsAreSerializedWithoutLostStateUpdates() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Add> { state, event -> Evolve(state + event.amount) }
        }
        val runtime = Run(application, 0, backgroundScope) { _, _ -> }
        for (i in 1..20) runtime.send(Event.Add(i))
        runCurrent()
        assertEquals((1..20).sum(), runtime.state.value)
        runtime.stop()
    }

    @Test fun commandsAreInterpretedInDeclarationOrderWithinOneTransition() = runTest {
        val observed = mutableListOf<Command>()
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ ->
                Evolve(state + 1, Command.First, Command.Second)
            }
        }
        val runtime = Run(application, 0, backgroundScope) { command, _ ->
            if (command == Command.First) delay(100)
            observed.add(command)
        }
        runtime.send(Event.Start)
        runCurrent()
        assertTrue(observed.isEmpty(), "Second must not overtake a suspending First")
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(Command.First, Command.Second), observed)
        runtime.stop()
    }

    @Test fun slowEffectDoesNotBlockFurtherStateTransitions() = runTest {
        var effectStarted = false
        var effectCancelled = false
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1, Command.Slow) }
            on<Event.Add> { state, event -> Evolve(state + event.amount) }
        }
        val runtime = Run(application, 0, backgroundScope) { _, _ ->
            effectStarted = true
            try { delay(100_000) } finally { effectCancelled = true }
        }
        runtime.send(Event.Start)
        runCurrent()
        assertTrue(effectStarted)
        runtime.send(Event.Add(41))
        runCurrent()
        assertEquals(42, runtime.state.value, "The event consumer must remain responsive")
        runtime.stop()
        runCurrent()
        assertTrue(effectCancelled, "Stop cancels interpreter children")
    }

    @Test fun boundedOfferReturnsFalseInsteadOfSilentlyDroppingOrGrowing() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1) }
        }
        val runtime = Run(application, 0, backgroundScope, queueCapacity = 1) { _, _ -> }
        // The StandardTestDispatcher has not started the consumer yet.
        assertTrue(runtime.offer(Event.Start))
        assertFalse(runtime.offer(Event.Start), "The queue's capacity is explicitly one")
        runCurrent()
        assertEquals(1, runtime.state.value)
        assertTrue(runtime.offer(Event.Start))
        runCurrent()
        assertEquals(2, runtime.state.value)
        runtime.stop()
    }

    @Test fun zeroCapacityMeansRendezvousNotUnboundedBuffering() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1) }
        }
        val runtime = Run(application, 0, backgroundScope, queueCapacity = 0) { _, _ -> }
        assertFalse(runtime.offer(Event.Start))
        // send suspends until the event consumer is able to receive.
        runtime.send(Event.Start)
        runCurrent()
        assertEquals(1, runtime.state.value)
        runtime.stop()
    }

    @Test fun expectedEffectFailureCanBeTranslatedIntoATypedEvent() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1, Command.Load) }
            on<Event.Failed> { state, _ -> Evolve(state - 1) }
        }
        val runtime = Run(
            flow = application,
            initialState = 0,
            scope = backgroundScope,
            onCommandFailure = { command, throwable ->
                assertEquals(Command.Load, command)
                assertEquals("network down", throwable.message)
                Event.Failed
            }
        ) { _, _ -> throw IllegalStateException("network down") }
        runtime.send(Event.Start)
        runCurrent()
        assertEquals(0, runtime.state.value)
        runtime.stop()
    }

    @Test fun cancellationDoesNotGetTranslatedIntoAnOrdinaryFailureEvent() = runTest {
        var failureCount = 0
        var cancelled = false
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state, Command.Slow) }
            on<Event.Failed> { state, _ -> Evolve(state + 100) }
        }
        val runtime = Run(
            flow = application, initialState = 0, scope = backgroundScope,
            onCommandFailure = { _, _ -> failureCount++; Event.Failed }
        ) { _, _ ->
            try { delay(1_000_000) } finally { cancelled = true }
        }
        runtime.send(Event.Start)
        runCurrent()
        runtime.stop()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, failureCount)
        assertEquals(0, runtime.state.value)
    }

    @Test fun stopIsIdempotentAndRejectsFutureEventsWithoutCancellingTheHost() = runTest {
        val parent = backgroundScope.coroutineContext[Job]!!
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1) }
        }
        val runtime = Run(application, 0, backgroundScope) { _, _ -> }
        runtime.send(Event.Start)
        runCurrent()
        assertEquals(1, runtime.state.value)
        runtime.stop()
        runtime.stop()
        assertFalse(runtime.offer(Event.Start))
        assertTrue(parent.isActive, "Borrowed owner scope must not be cancelled")
    }

    @Test fun cancellingTheHostAlsoStopsTheChildRunner() = runTest {
        val job = SupervisorJob()
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        val application = Flow<Int, Event, Command> {
            on<Event.Start> { state, _ -> Evolve(state + 1) }
        }
        val runtime = Run(application, 0, owner) { _, _ -> }
        runtime.send(Event.Start)
        runCurrent()
        assertEquals(1, runtime.state.value)
        job.cancel()
        runCurrent()
        assertFalse(runtime.offer(Event.Start))
    }

    @Test fun invalidCapacityIsAnExplicitConfigurationError() = runTest {
        val application = Flow<Int, Event, Command> {}
        assertFailsWith<IllegalArgumentException> {
            Run(application, 0, backgroundScope, queueCapacity = -1) { _, _ -> }
        }
        assertFailsWith<IllegalArgumentException> {
            Run(application, 0, backgroundScope, queueCapacity = 100_000) { _, _ -> }
        }
    }

    @Test fun snapshotsAreReadOnlyObservationsNotStoreOwnership() = runTest {
        val application = Flow<Int, Event, Command> {
            on<Event.Add> { state, event -> Evolve(state + event.amount) }
        }
        val runtime = Run(application, 4, backgroundScope) { _, _ -> }
        assertEquals(4, runtime.state.value)
        runtime.send(Event.Add(5))
        runCurrent()
        assertEquals(9, runtime.state.value)
        runtime.stop()
        assertEquals(9, runtime.state.value)
    }
}
