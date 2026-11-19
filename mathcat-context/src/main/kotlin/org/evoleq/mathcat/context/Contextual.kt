package org.evoleq.mathcat.context

import java.util.UUID

/** Faithful Solyton authenticated context; no dependency on Ktor. */
data class Contextual<T>(
    val userId: UUID,
    val context: String,
    val data: T
)

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(userId, context, f(data))
