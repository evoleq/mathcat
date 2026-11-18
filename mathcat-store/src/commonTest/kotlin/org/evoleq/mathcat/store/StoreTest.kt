package org.evoleq.mathcat.store

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.times
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Memory
import org.evoleq.mathcat.core.storage.writeWith
import org.evoleq.mathcat.core.writer.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StoreTest {
    data class Part(val name: String)
    data class Whole(val part: Part, val revision: Int)

    private val partLens = Lens(
        get = Reader<Whole, Part> { it.part },
        set = Writer<Whole, Part> { part -> { whole -> whole.copy(part = part) } }
    )
    private val nameLens = Lens(
        get = Reader<Part, String> { it.name },
        set = Writer<Part, String> { name -> { part -> part.copy(name = name) } }
    )

    private fun interface WriteDispatcher<S> {
        fun dispatch(command: Writer<S, String>)
    }

    private fun <S, A> lift(
        dispatcher: WriteDispatcher<S>,
        lens: Lens<S, A>
    ): WriteDispatcher<A> = WriteDispatcher { command ->
        dispatcher.dispatch(lens * command)
    }

    private fun fixture(): Pair<Memory<Whole>, Store<Whole, WriteDispatcher<Whole>>> {
        val backing = Memory(Whole(Part("initial"), revision = 11))
        val dispatcher = WriteDispatcher<Whole> { command ->
            backing.writeWith(command, "changed")
        }
        return backing to Store(storage = backing, dispatcher = dispatcher)
    }

    @Test
    fun storeDelegatesReadAndWriteToExistingStorage() {
        val memory = Memory(Whole(Part("first"), revision = 11))
        val store = memory.withDispatcher("recording")
        assertSame(memory, store.storage)
        assertEquals(memory.read(), store.read())
        store.write(Whole(Part("second"), revision = 12))
        assertEquals(Whole(Part("second"), revision = 12), memory.read())
    }

    @Test
    fun changingDispatcherDoesNotReplaceExistingStorage() {
        val memory = Memory(1)
        val original = memory.withDispatcher("first")
        val replacement = original.withDispatcher("second")
        assertSame(memory, original.storage)
        assertSame(memory, replacement.storage)
        assertEquals("first", original.dispatcher)
        assertEquals("second", replacement.dispatcher)
        replacement.write(6)
        assertEquals(6, original.read())
    }

    @Test
    fun focusingTransportsStateAndDispatcher() {
        val (backing, root) = fixture()
        val part = root.focus(partLens, ::lift)
        assertEquals(Part("initial"), part.read())
        part.dispatcher.dispatch(
            Writer { name: String -> { p: Part -> p.copy(name = name) } }
        )
        assertEquals(Whole(Part("changed"), 11), backing.read())
        assertEquals(Part("changed"), part.read())
    }

    @Test
    fun nestedFocusMatchesComposedLensForDispatch() {
        val (backing, root) = fixture()
        val nested = root.focus(partLens, ::lift).focus(nameLens, ::lift)
        val direct = root.focus(partLens * nameLens, ::lift)
        val change = Writer<String, String> { name -> { _ -> name } }

        nested.dispatcher.dispatch(change)
        val nestedResult = backing.read()
        backing.write(Whole(Part("initial"), revision = 11))
        direct.dispatcher.dispatch(change)
        val directResult = backing.read()

        assertEquals(nestedResult, directResult)
        assertEquals(Whole(Part("changed"), revision = 11), directResult)
    }
}
