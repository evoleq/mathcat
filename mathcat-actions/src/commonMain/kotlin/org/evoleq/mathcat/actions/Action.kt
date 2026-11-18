package org.evoleq.mathcat.actions

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.times
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.writer.Writer

/**
 * Typed, platform-independent description of an Action's state interaction.
 *
 * An Action does NOT execute its request. A runtime interpreter must decide
 * how its name/input are mapped to an endpoint, service or other effect.
 * Transport descriptors, serializers and HTTP-specific error codes are
 * intentionally not baked into the mathematical Action model.
 *
 * The optional onSuccess Writer is an additional state update hook. The
 * processor must explicitly decide whether/when to execute that hook.
 */
data class Action<S, I, O>(
    val name: String,
    val reader: Reader<S, I>,
    val writer: Writer<S, O>,
    val onSuccess: Writer<S, O>? = null
)

/**
 * Lift an Action from a focused state to a whole state through a lawful Lens.
 *
 * Each state-dependent member is lifted, including onSuccess. The operation
 * does not perform effects, and preserves the name.
 */
operator fun <S, A, I, O> Lens<S, A>.times(action: Action<A, I, O>): Action<S, I, O> =
    Action(
        name = action.name,
        reader = this * action.reader,
        writer = this * action.writer,
        onSuccess = action.onSuccess?.let { this * it }
    )
