package org.evoleq.mathcat.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTest {
    @Test
    fun sourceIsReaderAtUnitAndReevaluatesOnEveryEmit() {
        var current = 1
        val source: Source<Int> = Source { current }
        assertEquals(1, source(Unit))
        current = 2
        assertEquals(2, source.emit())
        assertEquals(2, source.read())
        assertEquals(4, source.map { it * 2 }.emit())
    }

    @Test
    fun sourceListEmitEvaluatesEachMemberInOrder() {
        var cursor = 0
        val a: Source<Int> = Source { ++cursor }
        val b: Source<Int> = Source { ++cursor }
        assertEquals(listOf(1, 2), listOf(a, b).emit())
        assertEquals(2, cursor)
    }
}
