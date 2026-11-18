package org.evoleq.mathcat.storage

import org.evoleq.mathcat.core.collections.FilterBy
import org.evoleq.mathcat.core.optics.IdLens
import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.reader.emit
import org.evoleq.mathcat.core.reader.map
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.core.writer.contraMap
import org.evoleq.mathcat.core.writer.dispatch
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageTest {
    data class Part(val name: String, val count: Int)
    data class Whole(val part: Part, val items: List<Int>)

    private val partLens = Lens<Whole, Part>(
        get = Reader { it.part },
        set = Writer { p -> { w -> w.copy(part = p) } }
    )
    private val nameLens = Lens<Part, String>(
        get = Reader { it.name },
        set = Writer { n -> { p -> p.copy(name = n) } }
    )
    private val itemsLens = Lens<Whole, List<Int>>(
        get = Reader { it.items },
        set = Writer { items -> { w -> w.copy(items = items) } }
    )

    private fun memory(initial: Whole): Pair<Storage<Whole>, () -> Whole> {
        var value = initial
        return Storage(read = { value }, write = { value = it }) to { value }
    }

    @Test
    fun lensFocusReadsAndWritesCurrentWholeWithoutReplacingUnrelatedFields() {
        val (store, current) = memory(Whole(Part("old", 4), listOf(1, 2, 3)))
        val name = store * partLens * nameLens

        assertEquals("old", name.read())
        name.write("new")
        assertEquals(Whole(Part("new", 4), listOf(1, 2, 3)), current())

        // This is a live view, not a captured snapshot.
        store.write(Whole(Part("latest", 7), listOf(5)))
        assertEquals("latest", name.read())
        name.write("again")
        assertEquals(Whole(Part("again", 7), listOf(5)), current())
    }

    @Test
    fun storageReaderCreatesLazySourceThatSeesFreshValues() {
        val (store, _) = memory(Whole(Part("first", 1), emptyList()))
        val readName: Reader<Whole, String> = Reader { it.part.name }
        val source: Source<String> = store * readName

        assertEquals("first", source.emit())
        store.write(Whole(Part("second", 2), emptyList()))
        assertEquals("second", source.emit())
        assertEquals("second", (source.map { it.uppercase() }).emit())
        assertEquals(store.read(), Read(store).emit())
    }

    @Test
    fun storageWriterProducesDeferredDispatcherAndSupportsContraMap() {
        val (store, current) = memory(Whole(Part("first", 1), emptyList()))
        val updater: Writer<Part, String> = Writer { name ->
            { part -> part.copy(name = name) }
        }
        val dispatchName = (store * partLens * updater)
            .contraMap { response: ApiResponse -> response.name }

        // Building a dispatcher has no side effects.
        assertEquals("first", current().part.name)
        dispatchName.dispatch(ApiResponse("updated"))
        assertEquals("updated", current().part.name)
        assertEquals(1, current().part.count)

        Dispatch(store).dispatch(Whole(Part("replaced", 3), listOf(1)))
        assertEquals("replaced", current().part.name)
        assertEquals(3, current().part.count)
    }

    data class ApiResponse(val name: String)

    @Test
    fun storageSelectionUsesOperationalSemanticsWithoutLensClaims() {
        val (store, current) = memory(Whole(Part("a", 0), listOf(1, 2, 3)))
        val even = store * itemsLens * FilterBy<Int> { it % 2 == 0 }

        assertEquals(listOf(2), even.read())
        even.write(listOf(8))
        assertEquals(listOf(1, 3, 8), current().items)
        even.write(even.read())
        assertEquals(listOf(1, 3, 8), current().items)
    }

    @Test
    fun identityLensOnStorageHasTheSameReadWriteBehavior() {
        val (store, current) = memory(Whole(Part("a", 0), listOf(1)))
        val focused = store * IdLens<Whole>()
        assertEquals(store.read(), focused.read())
        val replacement = Whole(Part("b", 1), listOf(3))
        focused.write(replacement)
        assertEquals(replacement, current())
    }
}
