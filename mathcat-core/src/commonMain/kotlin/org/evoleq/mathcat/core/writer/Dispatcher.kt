package org.evoleq.mathcat.core.writer

/**
 * A synchronous command-indexed Writer over the Unit state.
 *
 * Dispatcher<A> = Writer<Unit, A>. This is neither a coroutine dispatcher
 * nor the suspend-capable ActionDispatcher<S> from mathcat-actions.
 * Its Writer may perform effects when evaluated, not when constructed.
 */
typealias Dispatcher<A> = Writer<Unit, A>

/** Execute the selected command's Writer against the unique Unit value. */
infix fun <A> Dispatcher<A>.dispatch(command: A) {
    this(command)(Unit)
}
