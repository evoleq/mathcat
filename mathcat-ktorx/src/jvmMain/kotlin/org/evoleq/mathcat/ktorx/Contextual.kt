package org.evoleq.mathcat.ktorx

import java.util.UUID

/** Server-side Solyton Contextual, including authenticated user identity. */
data class Contextual<out T>(
    val userId: UUID,
    val context: String,
    val data: T
)

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(userId, context, f(data))
