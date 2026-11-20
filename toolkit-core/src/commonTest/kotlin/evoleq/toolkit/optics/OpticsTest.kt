package evoleq.toolkit.core.optics

import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.core.collections.FilterBy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OpticsTest {
    data class Details(val count: Int, val title: String)
    data class Whole(val details: Details, val items: List<Int>)

    private val detailsLens = Lens<Whole, Details>(
        get = Reader { it.details },
        set = Writer { value -> { whole -> whole.copy(details = value) } }
    )
    private val countLens = Lens<Details, Int>(
        get = Reader { it.count },
        set = Writer { value -> { details -> details.copy(count = value) } }
    )
    private val itemsLens = Lens<Whole, List<Int>>(
        get = Reader { it.items },
        set = Writer { items -> { whole -> whole.copy(items = items) } }
    )

    private val source = Whole(Details(2, "a"), listOf(1, 2, 3))

    private fun <S, A> assertLensLaws(
        lens: Lens<S, A>,
        state: S,
        first: A,
        second: A
    ) {
        // Get-Put
        assertEquals(state, lens.set(lens.get(state))(state))
        // Put-Get
        assertEquals(first, lens.get(lens.set(first)(state)))
        // Put-Put
        assertEquals(
            lens.set(second)(state),
            lens.set(second)(lens.set(first)(state))
        )
    }

    @Test
    fun lawfulLensesPreserveGetPutPutGetPutPut() {
        assertLensLaws(detailsLens, source, Details(3, "b"), Details(4, "c"))
        assertLensLaws(countLens, source.details, 9, 10)
        assertLensLaws(detailsLens * countLens, source, 9, 10)
        assertLensLaws(IdLens<Whole>(), source, source.copy(items = emptyList()), source)
    }

    @Test
    fun lensAssociativityAndIdentityAgreeOnReadsAndWrites() {
        val id = IdLens<Int>()
        val left = (detailsLens * countLens) * id
        val right = detailsLens * (countLens * id)
        assertEquals(left.get(source), right.get(source))
        assertEquals(left.set(12)(source), right.set(12)(source))
        assertEquals((IdLens<Whole>() * detailsLens).get(source), detailsLens.get(source))
    }

    @Test
    fun lensCanLiftBothReaderAndWriterWithoutLosingDomainContext() {
        val titleLength = Reader<Details, Int> { it.title.length }
        val increment = Writer<Details, Int> { delta ->
            { details -> details.copy(count = details.count + delta) }
        }

        assertEquals(1, (detailsLens * titleLength)(source))
        assertEquals(Whole(Details(5, "a"), listOf(1, 2, 3)), (detailsLens * increment)(3)(source))
    }

    @Test
    fun filterByIsComposableButNotLawfulLens() {
        val selection = FilterBy<Int> { it % 2 == 0 }
        val list = listOf(1, 2, 3)
        assertEquals(listOf(2), selection.get(list))
        assertEquals(listOf(1, 3, 2), selection.set(selection.get(list))(list))
        assertNotEquals(list, selection.set(selection.get(list))(list))
        assertEquals(listOf(1, 3, 8), selection.set(listOf(8))(list))

        val lifted: Selection<Whole, List<Int>> = itemsLens * selection
        assertEquals(listOf(2), lifted.get(source))
        assertEquals(source.copy(items = listOf(1, 3, 8)), lifted.set(listOf(8))(source))
    }

    @Test
    fun selectionsComposeWithSelectionsAndLenses() {
        val selector = itemsLens.asSelection()
        val evens = FilterBy<Int> { it % 2 == 0 }
        val left = (selector * evens) * IdLens<List<Int>>()
        val right = selector * (evens * IdLens<List<Int>>())

        assertEquals(left.get(source), right.get(source))
        assertEquals(left.set(listOf(4, 6))(source), right.set(listOf(4, 6))(source))

        val nested: Selection<Whole, List<Int>> = selector * evens
        val count = Reader<List<Int>, Int> { it.size }
        val clear = Writer<List<Int>, Unit> { _ -> { _ -> emptyList() } }
        assertEquals(1, (nested * count)(source))
        assertEquals(source.copy(items = listOf(1, 3)), (nested * clear)(Unit)(source))
    }
}
