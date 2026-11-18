package org.evoleq.mathcat.core

/**
 * A pure, nominal reader from [S] to [A].
 *
 * Nominal Reader and Writer types avoid the overload ambiguity of function typealiases,
 * while preserving the small, lambda-based API and explicit composition.
 */
fun interface Reader<in S, out A> {
    operator fun invoke(source: S): A
}

/**
 * A pure, curried state updater. The first argument is an update command, and the
 * resulting function transforms a state of type [S].
 *
 * In contrast to the traditional Writer monad, this Writer means a state updater.
 * It is deliberately NOT claimed to be a HKT or a Monad instance.
 */
fun interface Writer<S, in A> {
    operator fun invoke(value: A): (S) -> S
}

/** Covariant mapping of a Reader's result. */
fun <S, A, B> Reader<S, A>.map(f: (A) -> B): Reader<S, B> =
    Reader { source -> f(this(source)) }

/** Composition goes left-to-right: first this Reader, then [next]. */
operator fun <S, A, B> Reader<S, A>.times(next: Reader<A, B>): Reader<S, B> =
    Reader { source -> next(this(source)) }

/** Contravariant mapping of the input to a state updater. */
fun <S, A, B> Writer<S, A>.contraMap(f: (B) -> A): Writer<S, B> =
    Writer { value -> this(f(value)) }

/** Apply an update command to a concrete state. */
fun <S, A> Writer<S, A>.write(value: A, to: S): S = this(value)(to)

/** Identity reader for any state. */
fun <S> identityReader(): Reader<S, S> = Reader { it }
