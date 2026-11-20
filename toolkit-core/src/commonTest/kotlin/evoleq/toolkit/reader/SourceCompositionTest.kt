package evoleq.toolkit.reader

import evoleq.toolkit.core.reader.Source
import evoleq.toolkit.core.reader.emit
import evoleq.toolkit.core.reader.read
import evoleq.toolkit.core.reader.x
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceCompositionTest {
    @Test
    fun sourceProductIsDeferredLiveAndLeftToRight() {
        val observations = mutableListOf<String>()
        var leftValue = 1
        var rightValue = "a"
        val left: Source<Int> = Source { observations += "left"; leftValue }
        val right: Source<String> = Source { observations += "right"; rightValue }
        val product = left x right

        assertEquals(emptyList(), observations)
        assertEquals(1 to "a", product.emit())
        assertEquals(listOf("left", "right"), observations)

        leftValue = 2
        rightValue = "b"
        assertEquals(2 to "b", product.read())
        assertEquals(listOf("left", "right", "left", "right"), observations)
    }

    @Test
    fun listOfSourcesEmitsInOrderAndFreshEachTime() {
        var value = 1
        val sources = listOf(Source { value }, Source { value + 10 })
        assertEquals(listOf(1, 11), sources.emit())
        value = 3
        assertEquals(listOf(3, 13), sources.emit())
    }
}
