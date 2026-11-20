package evoleq.toolkit.core.optics

import evoleq.toolkit.types.Sum2

/**
 * A monomorphic Prism selects one case of a sum.
 *
 * match : S -> A + S
 * build : A -> S
 *
 * In the Evoleq convention Left denotes a successful match, Right is
 * the unmatched source. For a lawful Prism:
 *
 *   match(build(a)) == Left(a)                     (review)
 *   match(s) == Left(a)  =>  build(a) == s         (preview)
 *
 * Kotlin cannot prove these laws; common tests document them.
 * This is a PURE optic: loading resources on a miss is a separate effect.
 */
data class Prism<S, A>(
    val match: (S) -> Sum2<A, S>,
    val build: (A) -> S
)

/** Composition first focuses the outer sum case, then the inner one. */
operator fun <S, A, B> Prism<S, A>.times(
    inner: Prism<A, B>
): Prism<S, B> = Prism(
    match = { source ->
        when (val outerMatch = match(source)) {
            is Sum2.Right -> outerMatch
            is Sum2.Left -> when (val nested = inner.match(outerMatch.value)) {
                is Sum2.Left -> nested
                is Sum2.Right -> Sum2.Right(build(nested.value))
            }
        }
    },
    build = { value -> build(inner.build(value)) }
)
