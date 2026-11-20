package evoleq.toolkit.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTest {
    @Test
    fun sourceIsJustReaderFromUnitAndComposesWithReader() {
        val source: Source<Int> = Source { 7 }
        val result: Source<String> = source * Reader<Int, String> { "n=$it" }

        assertEquals(7, source.emit())
        assertEquals("n=7", result.emit())
        assertEquals(7, source(Unit))
    }

    @Test
    fun sourceEvaluationIsDeferred() {
        var calls = 0
        val source = Source { ++calls }

        assertEquals(0, calls)
        assertEquals(1, source.emit())
        assertEquals(2, source.emit())
    }
}
