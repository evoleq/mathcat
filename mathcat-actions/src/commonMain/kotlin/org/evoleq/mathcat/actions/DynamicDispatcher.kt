package org.evoleq.mathcat.actions

import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.reader.emit

/**
 * Late-bound dispatcher selection from a Source<ActionDispatcher<S>>.
 *
 * The current target is resolved on *every* dispatch invocation. This is
 * useful when previously focused module Stores must retain their identity
 * but the application's implementation changes (testing, reconnection,
 * environment switch, recording, DI).
 *
 * The Source is not required to be Compose-observable. This adapter does
 * not store, own, close, create or launch the selected dispatcher, and it
 * never executes any action by itself.
 *
 * The supplied source must not resolve to this DynamicDispatcher itself,
 * as that would cause unbounded forwarding recursion.
 */
@Suppress("FunctionName")
fun <S> DynamicDispatcher(
    current: Source<ActionDispatcher<S>>
): ActionDispatcher<S> = object : ActionDispatcher<S> {

    override suspend fun <I, O> dispatch(action: Action<S, I, O>) {
        current.emit().dispatch(action)
    }

    override suspend fun <I, O> dispatchEnvelope(envelope: ActionEnvelope<S, I, O>) {
        current.emit().dispatchEnvelope(envelope)
    }
}
