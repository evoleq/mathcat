package org.evoleq.mathcat.core.reader

/**
 * A pure, nominal reader from [S] to [A].
 *
 * Nominal Reader and Writer types avoid the overload ambiguity of function typealiases,
 * while preserving the small, lambda-based API and explicit composition.
 */
fun interface Reader<in S, out A> {
    operator fun invoke(source: S): A
}

/** Covariant mapping of a Reader's result. */
fun <S, A, B> Reader<S, A>.map(f: (A) -> B): Reader<S, B> =
    Reader { source -> f(this(source)) }

/** Composition goes left-to-right: first this Reader, then [next]. */
operator fun <S, A, B> Reader<S, A>.times(next: Reader<A, B>): Reader<S, B> =
    Reader { source -> next(this(source)) }

/**
 * Identity morphism on S, as a Reader.
 *
 * IdReader : S -> S,  IdReader(s) = s.
 * The capitalized standalone constructor follows Evoleq's DSL vocabulary.
 */
@Suppress("FunctionName")
fun <S> IdReader(): Reader<S, S> = Reader { it }
