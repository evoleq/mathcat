package org.evoleq.mathcat.core

/**
 * Pure state computation: a state is transformed into a result and a successor state.
 * It is intentionally NOT suspend; async/suspending state belongs to an effects module.
 */
fun interface State<S, out A> {
    fun run(state: S): Pair<A, S>

    companion object {
        /** State-monadic unit (pure/return) lives on the constructor, not a state value. */
        fun <S, A> pure(value: A): State<S, A> = State { state -> value to state }

        /** Inspect the current state without changing it. */
        fun <S> get(): State<S, S> = State { state -> state to state }

        /** Modify the state and return Unit. */
        fun <S> modify(transform: (S) -> S): State<S, Unit> =
            State { state -> Unit to transform(state) }
    }
}

operator fun <S, A> State<S, A>.invoke(initial: S): Pair<A, S> = run(initial)

/** Map only the returned value, preserving the successor state. */
fun <S, A, B> State<S, A>.map(f: (A) -> B): State<S, B> = State { state ->
    val (value, nextState) = run(state)
    f(value) to nextState
}

/** State monad bind: the next computation observes the updated state. */
fun <S, A, B> State<S, A>.flatMap(f: (A) -> State<S, B>): State<S, B> =
    State { state ->
        val (value, nextState) = run(state)
        f(value).run(nextState)
    }

fun <S, A, B> State<S, A>.bind(f: (A) -> State<S, B>): State<S, B> = flatMap(f)

/** Left-to-right Kleisli composition of a State and the next computation. */
operator fun <S, A, B> State<S, A>.times(
    f: (A) -> State<S, B>
): State<S, B> = flatMap(f)
