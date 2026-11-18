package org.evoleq.mathcat.core.reader

/**
 * A Source is a Reader without input, represented by Reader<Unit,T>.
 *
 * Source<T> = Hom(Unit,T). Unlike a materialized T, it is a deferred
 * computation and re-evaluates when emit() is called.
 *
 * This is a typealias (not a new runtime wrapper), deliberately preserving
 * the historic Evoleq vocabulary.
 */
typealias Source<T> = Reader<Unit, T>

/** Construct a Source from a zero-argument producer. */
@Suppress("FunctionName")
fun <T> Source(produce: () -> T): Source<T> =
    Reader { _: Unit -> produce() }

/** Evaluate a Source at Unit. */
fun <T> Source<T>.emit(): T = this(Unit)

/** Fluent alternative, useful for Source as a zero-input Reader. */
fun <T> Source<T>.read(): T = emit()

/** Evaluate sources in list order, once each. */
fun <T> List<Source<T>>.emit(): List<T> = map { it.emit() }
