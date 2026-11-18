package org.evoleq.mathcat.core.reader

/**
 * A Reader with no meaningful external input: Source<A> = Reader<Unit,A>.
 *
 * Source is intentionally a typealias (not a wrapper), so it participates in
 * the same Reader composition laws. Evaluating a Source can still perform
 * effects if supplied with an impure function; purity is not enforced by Kotlin.
 */
typealias Source<A> = Reader<Unit, A>

/** Construct a Source without exposing its Unit input in call sites. */
@Suppress("FunctionName")
fun <A> Source(read: () -> A): Source<A> = Reader { read() }

/** Evaluate the Source, providing the unique Unit argument. */
fun <A> Source<A>.emit(): A = this(Unit)
