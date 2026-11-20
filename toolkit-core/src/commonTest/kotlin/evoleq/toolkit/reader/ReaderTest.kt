package evoleq.toolkit.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTest {
    @Test
    fun readerCompositionPreservesIdentityAndAssociativity() {
        val f = Reader<Int, Int> { it + 2 }
        val g = Reader<Int, Int> { it * 3 }
        val h = Reader<Int, String> { "n=$it" }
        val id = IdReader<Int>()

        assertEquals(f(4), (id * f)(4))
        assertEquals(f(4), (f * id)(4))
        assertEquals(((f * g) * h)(4), (f * (g * h))(4))
        assertEquals("n=18", (f * g * h)(4))
        assertEquals("n=6", f.map { "n=$it" }(4))
    }
}
