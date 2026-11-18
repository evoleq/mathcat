package org.evoleq.mathcat.core.state

/**
 * Construct a pure State that returns the current state without changing it:
 *
 * Get<S>()(s) = (s, s).
 *
 * This is a DIFFERENT overload from Get(lens) / Get(selection), determined by
 * its zero-argument invocation and resulting State type.
 */
@Suppress("FunctionName")
fun <S> Get(): State<S, S> = State { s -> s to s }
