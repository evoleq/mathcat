package org.evoleq.mathcat.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StateTest {
    @Test
    fun pureAndMapDoNotChangeState() {
        assertEquals(3 to "s", State.pure<String, Int>(3)("s"))
        assertEquals(8 to "s", State.pure<String, Int>(3).map { it + 5 }("s"))
    }

    @Test
    fun flatMapPassesUpdatedStateToNextComputation() {
        val first = State<Int, Int> { s -> (s + 1) to (s + 10) }
        val second: (Int) -> State<Int, String> = { n ->
            State { s -> "${n + s}" to (s + 1) }
        }
        assertEquals("21" to 16, first.flatMap(second)(5))
        assertEquals(first.flatMap(second)(5), (first * second)(5))
    }

    @Test
    fun monadLeftIdentityRightIdentityAndAssociativity() {
        val f: (Int) -> State<Int, Int> = { a -> State { s -> (a + s) to (s + 1) } }
        val g: (Int) -> State<Int, String> = { a -> State { s -> "${a * 2}" to (s + 2) } }
        val m = State<Int, Int> { s -> (s + 3) to (s + 5) }

        for (s in listOf(-2, 0, 1, 7)) {
            // left identity
            assertEquals(f(4)(s), State.pure<Int, Int>(4).flatMap(f)(s))
            // right identity
            assertEquals(m(s), m.flatMap { State.pure<Int, Int>(it) }(s))
            // associativity
            assertEquals(
                m.flatMap(f).flatMap(g)(s),
                m.flatMap { a -> f(a).flatMap(g) }(s)
            )
        }
    }

    @Test
    fun getAndModifyHaveExplicitStateSemantics() {
        assertEquals(5 to 5, State.get<Int>()(5))
        assertEquals(Unit to 8, State.modify<Int> { it + 3 }(5))
    }
}
