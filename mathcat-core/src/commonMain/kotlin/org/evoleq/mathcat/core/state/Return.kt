package org.evoleq.mathcat.core.state

/**
 * Monad unit / return for the state type constructor T_S(A) = S -> (A, S).
 *
 * η_A : A -> T_S(A)
 * Return(value)(state) = (value, state)
 *
 * A standalone capitalized constructor deliberately avoids a companion
 * qualifier in Kleisli pipelines and retains Evoleq's mathematical vocabulary.
 * This does not provide a higher-kinded Monad interface.
 */
@Suppress("FunctionName")
fun <S, A> Return(value: A): State<S, A> =
    State { state -> value to state }
