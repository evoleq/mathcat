package org.evoleq.mathcat.actions

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.store.Store
import org.evoleq.mathcat.store.focus

/**
 * A ready-to-use, state-indexed Store specialization for ActionDispatchers.
 *
 * No changes to the generic Store or Storage algebra are required.
 * Both capabilities are transported along the *same* lawful Lens.
 */
operator fun <S, A> Store<S, ActionDispatcher<S>>.times(
    lens: Lens<S, A>
): Store<A, ActionDispatcher<A>> =
    focus(lens) { dispatcher, target -> dispatcher * target }

/** Delegate dispatch without exposing the capability record at every call. */
suspend fun <S, I, O> Store<S, ActionDispatcher<S>>.dispatch(action: Action<S, I, O>) {
    dispatcher.dispatch(action)
}

/** Preserve envelope semantics and metadata through a Store. */
suspend fun <S, I, O> Store<S, ActionDispatcher<S>>.dispatchEnvelope(
    envelope: ActionEnvelope<S, I, O>
) {
    dispatcher.dispatchEnvelope(envelope)
}
