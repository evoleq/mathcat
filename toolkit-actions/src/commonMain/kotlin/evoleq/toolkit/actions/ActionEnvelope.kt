package evoleq.toolkit.actions

import evoleq.toolkit.core.optics.Lens

/**
 * Metadata and ordered follow-up actions for a typed Action.
 *
 * next actions and deferred nextLazy factories may have different input and
 * output types, hence the existential/star projections in their lists.
 *
 * id, parentId, meta and execution/cleanup flags all have behavioral
 * significance; a Lens lift must not discard any of them.
 */
data class ActionEnvelope<S, I, O>(
    val action: Action<S, I, O>,
    val id: String,
    val parentId: String? = null,
    val next: List<ActionEnvelope<S, *, *>> = emptyList(),
    val nextLazy: List<() -> ActionEnvelope<S, *, *>> = emptyList(),
    val meta: Map<String, Any?> = emptyMap(),
    val run: Boolean = true,
    val clearOnFinish: Boolean = true
)

/**
 * Lift all nested Action descriptions without interpreting or executing them.
 *
 * Deferred nextLazy branches remain lazy. A lifted closure calls the original
 * supplier only when it is invoked, then lifts the resulting envelope.
 *
 * This is a value transformation; effects performed *inside* nextLazy
 * callbacks are the caller's responsibility, not promises of purity.
 */
operator fun <S, A, I, O> Lens<S, A>.times(
    envelope: ActionEnvelope<A, I, O>
): ActionEnvelope<S, I, O> =
    ActionEnvelope(
        action = this * envelope.action,
        id = envelope.id,
        parentId = envelope.parentId,
        next = envelope.next.map { this * it },
        nextLazy = envelope.nextLazy.map { supplier ->
            { this * supplier() }
        },
        meta = envelope.meta,
        run = envelope.run,
        clearOnFinish = envelope.clearOnFinish
    )
