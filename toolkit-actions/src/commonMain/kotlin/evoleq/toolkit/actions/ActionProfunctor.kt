package evoleq.toolkit.actions

import evoleq.toolkit.core.reader.map
import evoleq.toolkit.core.writer.contraMap

/**
 * Action<S, I, O> is covariant in I (Reader<S, I>) and contravariant in O
 * (Writer<S, O>). Written in the standard profunctor argument order,
 * P_S<O, I> = Action<S, I, O>.
 *
 * These are PURE descriptor transformations, not HTTP codecs. A transport
 * interpreter must still check the declared endpoint's request/response types.
 */

/** Covariant input map: the Reader produces a new value. */
fun <S, I, J, O> Action<S, I, O>.map(f: (I) -> J): Action<S, J, O> =
    Action(
        name = name,
        reader = reader.map(f),
        writer = writer,
        onSuccess = onSuccess
    )

/** Contravariant output map: both Writers accept a new response type. */
fun <S, I, O, P> Action<S, I, O>.contraMap(f: (P) -> O): Action<S, I, P> =
    Action(
        name = name,
        reader = reader,
        writer = writer.contraMap(f),
        onSuccess = onSuccess?.contraMap(f)
    )

/**
 * Standard profunctor dimap on P_S<O, I>: pre-map the first parameter
 * (new output -> old output), post-map the second (old input -> new input).
 */
fun <S, I, J, O, P> Action<S, I, O>.dimap(
    before: (P) -> O,
    after: (I) -> J
): Action<S, J, P> = map(after).contraMap(before)
