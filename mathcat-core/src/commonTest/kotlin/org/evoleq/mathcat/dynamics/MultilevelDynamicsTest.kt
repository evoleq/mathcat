package org.evoleq.mathcat.dynamics

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.writer.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Exploratory law/counterexample tests. No public Dynamics or Boundary API yet. */
class MultilevelDynamicsTest {
    data class Domain(val count: Int)
    data class Environment(val increment: Int)
    data class Whole(val domain: Domain, val environment: Environment)

    private val domainLens = Lens(
        get = Reader<Whole, Domain> { it.domain },
        set = Writer<Whole, Domain> { value -> { whole -> whole.copy(domain = value) } }
    )

    private val localStep: (Domain, Int) -> Domain =
        { domain, increment -> domain.copy(count = domain.count + increment) }

    // beta : Whole -> local driving input. Environment is not part of Domain.
    private fun coupledStep(whole: Whole): Whole =
        domainLens.set(localStep(domainLens.get(whole), whole.environment.increment))(whole)

    @Test
    fun liftedEvolutionAgreesWithLocalProjectionUnderExplicitCoupling() {
        val initial = Whole(Domain(0), Environment(increment = 2))
        val next = coupledStep(initial)
        assertEquals(
            localStep(domainLens.get(initial), initial.environment.increment),
            domainLens.get(next)
        )
        assertEquals(initial.environment, next.environment)

        // Repeating the same modeled tick yields the same local trajectory.
        val twice = coupledStep(next)
        assertEquals(Domain(4), twice.domain)
    }

    @Test
    fun sameDomainWithDifferentEnvironmentsMayHaveDifferentFutures() {
        val first = Whole(Domain(0), Environment(increment = 1))
        val second = Whole(Domain(0), Environment(increment = 3))

        assertEquals(domainLens.get(first), domainLens.get(second))
        assertNotEquals(domainLens.get(coupledStep(first)), domainLens.get(coupledStep(second)))
        // Therefore a standalone autonomous Domain -> Domain dynamic
        // cannot model both systems without the missing environmental input.
    }
}
