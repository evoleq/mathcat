package evoleq.toolkit.core.state

import kotlin.test.Test
import kotlin.test.assertEquals

class StateTest {
    @Test
    fun returnAndMapDoNotChangeState() {
        assertEquals(3 to "s", Return<String, Int>(3)("s"))
        assertEquals(8 to "s", Return<String, Int>(3).map { it + 5 }("s"))
    }

    @Test
    fun multiplicationUsesTheUpdatedStateFromTheOuterComputation() {
        val nested = State<Int, State<Int, String>> { initial ->
            State<Int, String> { current ->
                "input=$current" to (current + 1)
            } to (initial + 10)
        }

        // If multiply ran the inner computation on the original state,
        // the result would be ("input=5", 6), which would be wrong.
        assertEquals("input=15" to 16, nested.multiply()(5))
    }

    @Test
    fun bindThreadsStateThroughKleisliArrow() {
        val first = State<Int, Int> { s -> (s + 1) to (s + 10) }
        val second: KlState<Int, Int, String> = { n ->
            State { s -> "${n + s}" to (s + 1) }
        }

        assertEquals("21" to 16, first.bind(second)(5))
        assertEquals(first.bind(second)(5), (first * second)(5))
    }

    @Test
    fun monadLeftIdentityRightIdentityAndAssociativity() {
        val f: KlState<Int, Int, Int> = { a -> State { s -> (a + s) to (s + 1) } }
        val g: KlState<Int, Int, String> = { a -> State { s -> "${a * 2}" to (s + 2) } }
        val m = State<Int, Int> { s -> (s + 3) to (s + 5) }

        for (s in listOf(-2, 0, 1, 7)) {
            // left identity
            assertEquals(f(4)(s), (Return<Int, Int>(4) bind f)(s))
            // right identity
            assertEquals(m(s), (m bind { Return<Int, Int>(it) })(s))
            // associativity
            assertEquals(
                (m bind f bind g)(s),
                (m bind { a -> f(a) bind g })(s)
            )
        }
    }

    @Test
    fun getReadsTheStateWithoutChangingIt() {
        assertEquals(5 to 5, Get<Int>()(5))
    }

    @Test
    fun updatingStateAsKleisliArrowPreservesItsInputValue() {
        val update: KlState<Int, String, String> = Update { it + 3 }
        val start: State<Int, String> = Return("payload")

        // The current value is available to subsequent operations.
        val updated = start * update
        assertEquals("payload" to 8, updated(5))

        val observe: KlState<Int, String, String> = { value ->
            State { current -> "$value@$current" to current }
        }
        assertEquals("payload@8" to 8, (updated * observe)(5))
    }

    @Test
    fun kleisliUpdateActsAsIdentityOnValueNotOnState() {
        val update = Update<Int, Int> { it * 2 }
        assertEquals(9 to 8, update(9)(4))
        assertEquals(9 to 8, (Return<Int, Int>(9) bind update)(4))
    }
}
