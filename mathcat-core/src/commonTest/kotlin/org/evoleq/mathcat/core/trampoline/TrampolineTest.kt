package org.evoleq.mathcat.core.trampoline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Executable documentation: the same vocabulary as State/Result in MathCat:
 * Return, map, multiply and bind = map(f).multiply().
 */
class TrampolineTest {

    @Test
    fun returnMapMultiplyAndBindAreThePublicMonadVocabulary() {
        val input = Return(5)
        val f: (Int) -> Trampoline<String> = { n -> More { Return("n=$n") } }

        assertEquals(5, input.run())
        assertEquals(15, input.map { it * 3 }.run())
        assertEquals(5, Return(Return(5)).multiply().run())
        assertEquals("n=5", (input bind f).run())
        assertEquals("n=5", input.map(f).multiply().run())
        assertEquals("n=5", (input * f).run())
    }

    @Test
    fun moreAndMultiplyRemainLazyUntilRun() {
        var steps = 0
        val nested: Trampoline<Trampoline<Int>> = More {
            steps++
            Return(More {
                steps++
                Return(42)
            })
        }
        val flattened = nested.multiply()
        assertEquals(0, steps, "Building or multiplying must not force deferred work")
        assertEquals(42, flattened.run())
        assertEquals(2, steps)
        assertEquals(42, flattened.run())
        assertEquals(4, steps, "Trampoline is not memoized")
    }

    @Test
    fun functorIdentityAndCompositionLawsHoldObservationally() {
        val f: (Int) -> Int = { it + 7 }
        val g: (Int) -> String = { "value=$it" }
        for (value in listOf(-3, 0, 9)) {
            val computation = More { Return(value) }
            assertEquals(computation.run(), computation.map { it }.run())
            assertEquals(
                computation.map(f).map(g).run(),
                computation.map { g(f(it)) }.run()
            )
        }
    }

    @Test
    fun monadLeftIdentityRightIdentityAndAssociativityHoldObservationally() {
        val f: (Int) -> Trampoline<Int> = { n -> More { Return(n * 3) } }
        val g: (Int) -> Trampoline<String> = { n -> More { Return("result=$n") } }

        for (value in listOf(-2, 0, 4)) {
            val computation = More { Return(value + 2) }
            // Left unit: Return(a) bind f = f(a).
            assertEquals(f(value).run(), (Return(value) bind f).run())
            // Right unit: m bind Return = m.
            assertEquals(computation.run(), (computation bind ::Return).run())
            // Associativity: (m bind f) bind g = m bind (a -> f(a) bind g).
            assertEquals(
                ((computation bind f) bind g).run(),
                (computation bind { a -> f(a) bind g }).run()
            )
        }
    }

    @Test
    fun multiplicationRespectsLeftAndRightUnitAndAssociativity() {
        val value = More { Return(11) }
        // mu . eta_T = id; mu . T(eta) = id.
        assertEquals(value.run(), Return(value).multiply().run())
        assertEquals(value.run(), value.map(::Return).multiply().run())

        val triple = Return(More { Return(Return(11)) })
        // mu . mu_T = mu . T(mu)
        assertEquals(
            triple.multiply().multiply().run(),
            triple.map { it.multiply() }.multiply().run()
        )
    }

    @Test
    fun deepDeferredRecursionDoesNotConsumeTheCallStack() {
        fun countdown(n: Int, total: Int): Trampoline<Int> =
            if (n == 0) Return(total)
            else More { countdown(n - 1, total + 1) }

        assertEquals(50_000, countdown(50_000, 0).run())
    }

    @Test
    fun deepBindAndMultiplyChainsAreAlsoStackSafe() {
        var program: Trampoline<Int> = Return(0)
        repeat(50_000) {
            program = program bind { value -> Return(value + 1) }
        }
        assertEquals(50_000, program.run())

        var nested: Trampoline<Int> = Return(23)
        repeat(50_000) {
            nested = Return(nested).multiply()
        }
        assertEquals(23, nested.run())
    }

    @Test
    fun userExceptionsAreNotSwallowed() {
        val computation: Trampoline<Int> = More { error("failure from user thunk") }
        assertFailsWith<IllegalStateException> { computation.run() }
    }
}
