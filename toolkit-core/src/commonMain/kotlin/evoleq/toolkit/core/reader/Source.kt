package evoleq.toolkit.core.reader

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

/** Fluent evaluation of a Source, preserving the original Evoleq vocabulary. */
fun <A> Source<A>.read(): A = emit()

/** Evaluate every Source in order; nothing is evaluated while building the list. */
fun <A> List<Source<A>>.emit(): List<A> = map { source -> source.emit() }

/**
 * Product of deferred Sources. Neither side runs until emit() is called.
 * On each emission both sources are evaluated left to right.
 */
infix fun <A, B> Source<A>.x(other: Source<B>): Source<Pair<A, B>> {
    val left = this
    return Source { left.emit() to other.emit() }
}
