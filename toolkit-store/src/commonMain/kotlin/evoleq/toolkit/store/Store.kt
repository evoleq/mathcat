package evoleq.toolkit.store

import evoleq.toolkit.core.optics.Lens
import evoleq.toolkit.core.storage.Storage
import evoleq.toolkit.core.storage.times

/**
 * Couples existing state and dispatch capabilities.
 *
 * No Memory is allocated; no coroutine, collector or Action processor starts.
 * The dispatcher type D stays open to allow existing Evoleq dispatchers and
 * external platform adapters without introducing their dependencies into
 * the mathematical core.
 */
data class Store<S, D>(
    val storage: Storage<S>,
    val dispatcher: D
) : Storage<S> by storage {
    /**
     * Return another Store that shares Storage but has a new dispatcher.
     * Existing Store and focused Store values retain the old dispatcher.
     */
    fun <E> withDispatcher(newDispatcher: E): Store<S, E> =
        Store(storage = storage, dispatcher = newDispatcher)
}

/** Pair existing Storage with a dispatcher, without copying state. */
fun <S, D> Storage<S>.withDispatcher(dispatcher: D): Store<S, D> =
    Store(storage = this, dispatcher = dispatcher)

/**
 * Focus state and transport the dispatcher through the same Lens.
 *
 * Transport remains explicit because a generic dispatcher cannot lift an
 * arbitrary typed Action without its Action/Envelope semantics. An eventual
 * Action-specific adapter can offer the shorter Store * Lens operator.
 */
fun <S, A, D, E> Store<S, D>.focus(
    lens: Lens<S, A>,
    transport: (D, Lens<S, A>) -> E
): Store<A, E> = Store(
    storage = storage * lens,
    dispatcher = transport(dispatcher, lens)
)
