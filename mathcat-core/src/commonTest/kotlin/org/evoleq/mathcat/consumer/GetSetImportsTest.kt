package org.evoleq.mathcat.consumer

import org.evoleq.mathcat.core.optics.Get
import org.evoleq.mathcat.core.state.Get as StateGet
import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.optics.Set
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.core.collections.FilterBy
import org.evoleq.mathcat.core.reader.times
import org.evoleq.mathcat.core.writer.contraMap
import org.evoleq.mathcat.core.reader.maps.Get as MapGet
import org.evoleq.mathcat.core.reader.maps.GetForSure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GetSetImportsTest {
    data class User(val points: Int)

    private val points = Lens<User, Int>(
        get = Reader { user -> user.points },
        set = Writer { points -> { user -> user.copy(points = points) } }
    )

    @Test
    fun getAndSetExtractOperationsForFluentComposition() {
        val read = Get(points)
        val write = Set(points)
        val original = User(1)

        assertEquals(1, read(original))
        assertEquals(User(8), write(8)(original))
        assertEquals(2, (Get(points) * Reader<Int, Int> { it * 2 })(original))
        val fromText = Set(points).contraMap { text: String -> text.length }
        assertEquals(User(4), fromText("four")(original))
    }

    @Test
    fun selectionsHaveConstructorsWithoutLensLawClaims() {
        val even: Selection<List<Int>, List<Int>> = FilterBy { n: Int -> n % 2 == 0 }
        assertEquals(listOf(2), Get(even)(listOf(1, 2, 3)))
        assertEquals(listOf(1, 3, 8), Set(even)(listOf(8))(listOf(1, 2, 3)))
    }

    @Test
    fun stateGetConstructsAReadOnlyStateComputation() {
        assertEquals(7 to 7, StateGet<Int>()(7))
    }

    @Test
    fun mapGetCanBeImportedWithAliasAlongsideOpticsGet() {
        val map = mapOf("a" to 1, "b" to 2)
        assertEquals(2, MapGet<String, Int>("b")(map))
        assertEquals(null, MapGet<String, Int>("none")(map))
        assertEquals(1, GetForSure<String, Int>("a")(map))
        assertFails { GetForSure<String, Int>("none")(map) }
    }
}
