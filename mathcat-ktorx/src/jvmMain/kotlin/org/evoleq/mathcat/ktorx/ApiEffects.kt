package org.evoleq.mathcat.ktorx

/**
 * Suspendable state computation only for this Ktor adapter.
 * The pure MathCat Core State is intentionally not made suspending.
 */
fun interface Action<B, T> {
    suspend fun execute(base: B): Pair<T, B>
}

typealias KlAction<B, S, T> = suspend (S) -> Action<B, T>

/** Kleisli composition: the next action receives the updated state. */
operator fun <B, S, T> Action<B, S>.times(next: KlAction<B, S, T>): Action<B, T> =
    Action { base ->
        val (value, updated) = execute(base)
        next(value).execute(updated)
    }

/** Preserve the familiar Solyton runOn syntax. */
suspend infix fun <B, T> Action<B, T>.runOn(base: B): T = execute(base).first
