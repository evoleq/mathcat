package org.evoleq.mathcat.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderWriterTest {
    @Test
    fun readerCompositionPreservesIdentityAndAssociativity() {
        val f = Reader<Int, Int> { it + 2 }
        val g = Reader<Int, Int> { it * 3 }
        val h = Reader<Int, String> { "n=$it" }
        val id = identityReader<Int>()

        assertEquals(f(4), (id * f)(4))
        assertEquals(f(4), (f * id)(4))
        assertEquals(((f * g) * h)(4), (f * (g * h))(4))
        assertEquals("n=18", (f * g * h)(4))
        assertEquals("n=6", f.map { "n=$it" }(4))
    }

    @Test
    fun writerContraMapMakesApiOutputUsableByADomainUpdater() {
        data class Counter(val value: Int)
        data class ApiResponse(val updated: Int)

        val writeCounter = Writer<Counter, Int> { newValue ->
            { current -> current.copy(value = newValue) }
        }
        val writeApi = writeCounter.contraMap { response: ApiResponse -> response.updated }

        assertEquals(Counter(7), writeApi.write(ApiResponse(7), Counter(2)))
    }

    @Test
    fun contraMapPreservesIdentityAndComposition() {
        val writer = Writer<Int, Int> { by -> { old -> old + by } }
        val id = writer.contraMap { i: Int -> i }
        val f: (String) -> Int = { it.length }
        val g: (Boolean) -> String = { if (it) "yes" else "no" }

        assertEquals(writer(4)(10), id(4)(10))
        assertEquals(
            writer.contraMap(f).contraMap(g)(true)(10),
            writer.contraMap { b: Boolean -> f(g(b)) }(true)(10)
        )
    }
}
