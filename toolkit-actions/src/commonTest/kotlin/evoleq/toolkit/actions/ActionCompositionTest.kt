package evoleq.toolkit.actions

import evoleq.toolkit.core.optics.Lens
import evoleq.toolkit.core.optics.times
import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.storage.Memory
import evoleq.toolkit.core.storage.source
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.store.Store
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActionCompositionTest {
    data class Module(val name: String)
    data class Application(val module: Module, val revision: Int)

    private val moduleLens = Lens(
        get = Reader<Application, Module> { it.module },
        set = Writer<Application, Module> { value ->
            { whole -> whole.copy(module = value) }
        }
    )
    private val nameLens = Lens(
        get = Reader<Module, String> { it.name },
        set = Writer<Module, String> { value ->
            { module -> module.copy(name = value) }
        }
    )
    private val initial = Application(Module("before"), revision = 42)
    private val rename = Action(
        name = "rename",
        reader = Reader<Module, String> { it.name },
        writer = Writer<Module, String> { value ->
            { module -> module.copy(name = value) }
        },
        onSuccess = Writer<Module, String> { value ->
            { module -> module.copy(name = "confirmed:$value") }
        }
    )

    // Recording dispatchers do not suspend. We keep commonTest independent
    // from kotlinx.coroutines and explicitly reject unexpected suspension.
    private fun runImmediate(block: suspend () -> Unit) {
        var finished = false
        var failure: Throwable? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                failure = result.exceptionOrNull()
                finished = true
            }
        })
        check(finished) { "The synchronous recording dispatcher suspended unexpectedly" }
        failure?.let { throw it }
    }

    @Test
    fun liftedActionPreservesStateDependentSuccessHook() {
        val lifted: Action<Application, String, String> = moduleLens * rename
        assertEquals("rename", lifted.name)
        assertEquals("before", lifted.reader(initial))
        assertEquals(Application(Module("after"), 42), lifted.writer("after")(initial))
        assertEquals(
            Application(Module("confirmed:after"), 42),
            lifted.onSuccess!!("after")(initial)
        )
    }

    @Test
    fun envelopeLiftPreservesMetadataFlagsAndNestedActionsWithoutEagerLazyEvaluation() {
        var supplierCalls = 0
        val leaf = ActionEnvelope(
            action = rename,
            id = "child",
            parentId = "root",
            run = false,
            clearOnFinish = false,
            meta = mapOf("child-note" to 2)
        )
        val metadata = mapOf<String, Any?>("correlation-id" to "corr-42", "attempt" to 3)
        val envelope = ActionEnvelope(
            action = rename,
            id = "root",
            parentId = "request",
            next = listOf(leaf),
            nextLazy = listOf {
                supplierCalls += 1
                leaf.copy(id = "deferred")
            },
            meta = metadata,
            run = false,
            clearOnFinish = false
        )
        val lifted = moduleLens * envelope

        assertEquals(0, supplierCalls)
        assertEquals("root", lifted.id)
        assertEquals("request", lifted.parentId)
        assertSame(metadata, lifted.meta)
        assertEquals(false, lifted.run)
        assertEquals(false, lifted.clearOnFinish)
        assertEquals(1, lifted.next.size)
        assertEquals("child", lifted.next.single().id)
        assertEquals("root", lifted.next.single().parentId)
        assertEquals(false, lifted.next.single().run)
        assertEquals(false, lifted.next.single().clearOnFinish)
        assertEquals("before", lifted.next.single().action.reader(initial))
        assertEquals(1, lifted.nextLazy.size)

        val deferred = lifted.nextLazy.single().invoke()
        assertEquals(1, supplierCalls)
        assertEquals("deferred", deferred.id)
        assertEquals("before", deferred.action.reader(initial))
        assertEquals(false, deferred.run)
        assertEquals(false, deferred.clearOnFinish)
    }

    @Test
    fun storeLensComposesStateAndDispatcherWithoutCreatingNewMemory() {
        val memory = Memory(initial)
        val delivered = mutableListOf<ActionEnvelope<Application, *, *>>()
        val rootDispatcher: ActionDispatcher<Application> = ActionDispatcher { delivered += it }
        val store: Store<Application, ActionDispatcher<Application>> =
            Store(storage = memory, dispatcher = rootDispatcher)
        val local = store * moduleLens

        assertSame(memory, store.storage)
        assertEquals(Module("before"), local.read())
        local.write(Module("local"))
        assertEquals(Application(Module("local"), 42), memory.read())

        val next = ActionEnvelope(
            action = rename,
            id = "rename-id",
            meta = mapOf("source" to "test"),
            run = true,
            clearOnFinish = false
        )
        runImmediate { local.dispatchEnvelope(next) }

        assertEquals(1, delivered.size)
        val received = delivered.single()
        assertEquals("rename-id", received.id)
        assertEquals(mapOf("source" to "test"), received.meta)
        assertTrue(received.run)
        assertEquals(false, received.clearOnFinish)
        assertEquals("local", received.action.reader(memory.read()))
    }

    @Test
    fun consecutiveFocusingAndComposedFocusingAgreeForTypedActions() {
        val action = Action(
            name = "set-name",
            reader = Reader<String, String> { it },
            writer = Writer<String, String> { new -> { _ -> new } },
            onSuccess = Writer<String, String> { new -> { _ -> "confirmed:$new" } }
        )
        val nested = moduleLens * (nameLens * action)
        val direct = (moduleLens * nameLens) * action

        assertEquals(nested.reader(initial), direct.reader(initial))
        assertEquals(nested.writer("next")(initial), direct.writer("next")(initial))
        assertEquals(
            nested.onSuccess!!("next")(initial),
            direct.onSuccess!!("next")(initial)
        )

        val events = mutableListOf<ActionEnvelope<Application, *, *>>()
        val root = Store(
            storage = Memory(initial),
            dispatcher = ActionDispatcher<Application> { events += it }
        )
        val local = (root * moduleLens) * nameLens
        val directStore = root * (moduleLens * nameLens)
        runImmediate {
            local.dispatch(action)
            directStore.dispatch(action)
        }
        assertEquals(2, events.size)
        assertEquals(events[0].action.name, events[1].action.name)
        assertEquals(
            events[0].action.reader(initial),
            events[1].action.reader(initial)
        )
    }

    @Test
    fun replacedRootDispatcherCanServeDifferentFocusedStoreWithoutReplacingState() {
        val memory = Memory(initial)
        val firstEvents = mutableListOf<ActionEnvelope<Application, *, *>>()
        val secondEvents = mutableListOf<ActionEnvelope<Application, *, *>>()

        val first = Store(
            storage = memory,
            dispatcher = ActionDispatcher<Application> { firstEvents += it }
        )
        val oldFocused = first * moduleLens
        val replacement = first.withDispatcher(
            ActionDispatcher<Application> { secondEvents += it }
        )
        val newFocused = replacement * moduleLens

        assertSame(memory, first.storage)
        assertSame(memory, replacement.storage)

        runImmediate {
            oldFocused.dispatch(rename)
            newFocused.dispatch(rename)
            oldFocused.dispatch(rename)
        }

        assertEquals(2, firstEvents.size)
        assertEquals(1, secondEvents.size)
        assertEquals(initial, memory.read()) // Dispatcher only accepts messages.
    }

    @Test
    fun currentSourceDispatcherIsUsedByAlreadyFocusedModuleStore() {
        val backing = Memory(initial)
        val eventsOne = mutableListOf<ActionEnvelope<Application, *, *>>()
        val eventsTwo = mutableListOf<ActionEnvelope<Application, *, *>>()
        val one: ActionDispatcher<Application> = ActionDispatcher { eventsOne += it }
        val two: ActionDispatcher<Application> = ActionDispatcher { eventsTwo += it }

        // An ordinary non-observable Memory holds the active dispatcher.
        val selection = Memory<ActionDispatcher<Application>>(one)
        val application = Store(
            storage = backing,
            dispatcher = DynamicDispatcher(selection.source())
        )
        val module = application * moduleLens

        runImmediate { module.dispatch(rename) }
        assertEquals(1, eventsOne.size)
        assertEquals(0, eventsTwo.size)

        // The very same focused module retains the same Store reference.
        selection.write(two)
        runImmediate {
            module.dispatchEnvelope(
                ActionEnvelope(action = rename, id = "switched", run = false)
            )
        }
        assertEquals(1, eventsOne.size)
        assertEquals(1, eventsTwo.size)
        assertEquals("switched", eventsTwo.single().id)
        assertEquals(false, eventsTwo.single().run)
        assertSame(backing, application.storage)
        assertEquals(initial, backing.read())
    }

    @Test
    fun recordingDispatcherTreatsDispatchAsAcceptanceNotExecution() {
        val delivered = mutableListOf<ActionEnvelope<Module, *, *>>()
        val dispatcher = ActionDispatcher<Module> { delivered += it }
        runImmediate { dispatcher.dispatch(rename) }
        assertEquals(1, delivered.size)
        assertEquals("rename", delivered.single().id)
        assertEquals("rename", delivered.single().action.name)
        // The sink only records the action; it never executes the Writer.
    }
}
