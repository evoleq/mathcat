package org.evoleq.mathcat.core

/**
 * A stateful Kleisli arrow: given an input [A], produce a pure state computation
 * which returns a value of type [B].
 *
 * This is a normal function type, not necessarily a Kleisli arrow in a
 * suspendable/effectful runtime. Those variants belong to an effects module.
 */
typealias KlState<S, A, B> = (A) -> State<S, B>

/**
 * Pure state computation: state S -> (result A, next state S).
 *
 * It is intentionally NOT suspend; suspending state belongs to an effects module.
 * Monad operations retain the established Evoleq names Return, map,
 * multiply and bind.
 */
fun interface State<S, out A> {
    fun run(state: S): Pair<A, S>

    companion object {
        /** Monad return / unit: a value becomes a computation without changing S. */
        @Suppress("FunctionName")
        fun <S, A> Return(value: A): State<S, A> =
            State { state -> value to state }

        /** Inspect the current state, returning that state without changing it. */
        fun <S> get(): State<S, S> =
            State { state -> state to state }

    }
}

operator fun <S, A> State<S, A>.invoke(initial: S): Pair<A, S> = run(initial)

/** Functor mapping: transform only the result; preserve the successor state. */
fun <S, A, B> State<S, A>.map(f: (A) -> B): State<S, B> =
    State { state ->
        val (value, nextState) = run(state)
        f(value) to nextState
    }

/**
 * Monad multiplication / join. Run the inner State on the *updated* state
 * produced by the outer State, not on the original state.
 */
fun <S, A> State<S, State<S, A>>.multiply(): State<S, A> =
    State { state ->
        val (inner, nextState) = run(state)
        inner.run(nextState)
    }

/** Monad bind is derived from map and multiplication. */
infix fun <S, A, B> State<S, A>.bind(
    kleisli: KlState<S, A, B>
): State<S, B> = map(kleisli).multiply()

/** Left-to-right composition with a stateful Kleisli arrow. */
operator fun <S, A, B> State<S, A>.times(
    kleisli: KlState<S, A, B>
): State<S, B> = bind(kleisli)
