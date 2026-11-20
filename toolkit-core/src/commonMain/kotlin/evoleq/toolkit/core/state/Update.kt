package evoleq.toolkit.core.state

/**
 * Construct a state-changing Kleisli arrow that preserves the value it receives.
 *
 * For a state transformation u : S -> S, this defines
 *
 *     Update(u) : A -> State<S, A>
 *     Update(u)(a)(s) = (a, u(s))
 *
 * State and the input value are different dimensions: Update changes the state
 * while carrying the Kleisli value on for later arrows. Unlike an operation
 * returning Unit, it does not erase the value needed by subsequent composition.
 *
 * This constructor is intentionally top-level and capitalized, following Evoleq's
 * established naming conventions; no State companion qualifier is needed.
 *
 * Example:
 *
 *     val increment: KlState<Int, String, String> = Update { it + 1 }
 *     val result = (Return<Int, String>("payload") * increment)(5)
 *     // result == ("payload" to 6)
 */
@Suppress("FunctionName")
fun <S, A> Update(change: (S) -> S): KlState<S, A, A> =
    { value -> State { state -> value to change(state) } }
