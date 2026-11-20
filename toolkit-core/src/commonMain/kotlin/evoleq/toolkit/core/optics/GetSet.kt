package evoleq.toolkit.core.optics

import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.writer.Writer

/**
 * Extract the Reader from a lawful Lens.
 *
 * Get(lens) : Reader<S,A>, with Get(lens)(s) = lens.get(s).
 * The capitalized factory is the familiar Evoleq constructor; get remains
 * the operation on an existing Lens or Selection.
 */
@Suppress("FunctionName")
fun <S, A> Get(lens: Lens<S, A>): Reader<S, A> = lens.get

/**
 * Extract the Writer from a lawful Lens.
 *
 * Set(lens) : Writer<S,A>, with Set(lens)(a)(s) = lens.set(a)(s).
 */
@Suppress("FunctionName")
fun <S, A> Set(lens: Lens<S, A>): Writer<S, A> = lens.set

/** Selections also have Get and Set, without claiming Lens laws. */
@Suppress("FunctionName")
fun <S, A> Get(selection: Selection<S, A>): Reader<S, A> = selection.get

@Suppress("FunctionName")
fun <S, A> Set(selection: Selection<S, A>): Writer<S, A> = selection.set
