package org.evoleq.mathcat.evolution

/** One pure transition: a new state and an ordered description of effects. */
data class Evolution<S, out C>(val state: S, val commands: List<C>)

/** Constructor vocabulary: values, never running or launching effects. */
fun <S, C> Evolve(state: S, vararg commands: C): Evolution<S, C> =
    Evolution(state, commands.toList())

/** A pure, event-driven state transition (a Mealy-style step). */
typealias Transition<S, E, C> = (S, E) -> Evolution<S, C>

@DslMarker
annotation class FlowDsl

/**
 * Immutable set of ordered transition branches. First matching branch wins.
 * A Flow has no CoroutineScope, Storage ownership, IO, or implicit lifecycle.
 */
class Flow<S, E : Any, C> internal constructor(
    internal val branches: List<Branch<S, E, C>>,
    internal val fallback: Transition<S, E, C>?
) {
    /** The caller chooses how and when returned commands are interpreted. */
    fun evolve(state: S, event: E): Evolution<S, C> {
        val branch = branches.firstOrNull { it.matches(event) }
        return when {
            branch != null -> branch.transition(state, event)
            fallback != null -> fallback.invoke(state, event)
            else -> Evolution(state, emptyList())
        }
    }
}

internal class Branch<S, E : Any, C>(
    val matches: (E) -> Boolean,
    val transition: Transition<S, E, C>
)

@FlowDsl
class FlowConfiguration<S, E : Any, C> {
    private val branches = mutableListOf<Branch<S, E, C>>()
    private var fallback: Transition<S, E, C>? = null

    /** Include feature-local branches without creating a nested runtime. */
    fun include(fragment: Flow<S, E, C>) {
        require(fragment.fallback == null) { "A Flow fragment must not define otherwise" }
        branches.addAll(fragment.branches)
    }

    /** Predicate-based dispatch permits arbitrary sealed event representations. */
    fun on(matches: (E) -> Boolean, evolve: Transition<S, E, C>) {
        branches.add(Branch(matches, evolve))
    }

    /** Typed event dispatch. */
    inline fun <reified A : E> on(noinline evolve: (S, A) -> Evolution<S, C>) {
        on({ it is A }) { state, event ->
            @Suppress("UNCHECKED_CAST")
            evolve(state, event as A)
        }
    }

    /** Explicitly handle an unmatched event. May only be declared once. */
    fun otherwise(evolve: Transition<S, E, C>) {
        require(fallback == null) { "Only one otherwise branch is permitted" }
        fallback = evolve
    }

    fun configure(): Flow<S, E, C> = Flow(branches.toList(), fallback)
}

/** Capitalized, self-contained construction in the MathCat/Evoleq tradition. */
@Suppress("FunctionName")
fun <S, E : Any, C> Flow(configure: FlowConfiguration<S, E, C>.() -> Unit): Flow<S, E, C> =
    FlowConfiguration<S, E, C>().apply(configure).configure()

/** The identity transition. */
@Suppress("FunctionName")
fun <S, E, C> IdTransition(): Transition<S, E, C> = { state, _ ->
    Evolution(state, emptyList())
}

/**
 * Sequential composition in the State × Writer algebra.
 * Both stages see the same event; the second sees the first stage's state.
 * Commands concatenate in execution-description order. No commands execute here.
 */
operator fun <S, E, C> Transition<S, E, C>.times(
    next: Transition<S, E, C>
): Transition<S, E, C> = { state, event ->
    val first = this(state, event)
    val second = next(first.state, event)
    Evolution(second.state, first.commands + second.commands)
}
