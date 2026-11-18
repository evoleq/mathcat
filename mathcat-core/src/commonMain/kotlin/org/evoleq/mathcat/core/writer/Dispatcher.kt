package org.evoleq.mathcat.core.writer

/**
 * A command-indexed state updater with Unit as its state.
 *
 * Dispatcher<A> = Writer<Unit,A>.
 * It conveys a command to an effectful sink; it is NOT a coroutine dispatcher.
 */
typealias Dispatcher<A> = Writer<Unit, A>

/** Execute the effect selected by a command, discarding the Unit state. */
infix fun <A> Dispatcher<A>.dispatch(command: A): Unit {
    this(command)(Unit)
}
