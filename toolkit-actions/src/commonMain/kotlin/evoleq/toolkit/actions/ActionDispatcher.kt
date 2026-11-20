package evoleq.toolkit.actions

import evoleq.toolkit.core.optics.Lens

/**
 * An effect boundary that accepts typed Actions and Envelopes.
 *
 * Suspend means dispatch may wait; it does NOT imply the action completed.
 * Delivery, retry, backpressure, cancellation and exactly-once semantics
 * belong to the provided implementation.
 *
 * This interface does not start a processor or own coroutine lifecycle.
 */
interface ActionDispatcher<S> {
    suspend fun <I, O> dispatch(action: Action<S, I, O>)
    suspend fun <I, O> dispatchEnvelope(envelope: ActionEnvelope<S, I, O>)
}

/**
 * Adapt a single envelope sink to ActionDispatcher. Useful for recording,
 * queues and runtime adapters with clearly documented delivery semantics.
 */
@Suppress("FunctionName")
fun <S> ActionDispatcher(
    emit: suspend (ActionEnvelope<S, *, *>) -> Unit
): ActionDispatcher<S> = object : ActionDispatcher<S> {
    override suspend fun <I, O> dispatch(action: Action<S, I, O>) {
        emit(ActionEnvelope(action = action, id = action.name))
    }

    override suspend fun <I, O> dispatchEnvelope(envelope: ActionEnvelope<S, I, O>) {
        emit(envelope)
    }
}

/**
 * Project a root dispatcher into a submodule using the already-implemented
 * Lens * Action / Lens * ActionEnvelope lifting.
 */
operator fun <S, A> ActionDispatcher<S>.times(
    lens: Lens<S, A>
): ActionDispatcher<A> {
    val root = this
    return object : ActionDispatcher<A> {
        override suspend fun <I, O> dispatch(action: Action<A, I, O>) {
            root.dispatch(lens * action)
        }

        override suspend fun <I, O> dispatchEnvelope(envelope: ActionEnvelope<A, I, O>) {
            root.dispatchEnvelope(lens * envelope)
        }
    }
}
