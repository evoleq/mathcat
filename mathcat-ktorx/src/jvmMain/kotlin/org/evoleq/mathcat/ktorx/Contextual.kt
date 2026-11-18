package org.evoleq.mathcat.ktorx

import java.util.UUID
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.result.map

/**
 * The authenticated Contextual from the original Solyton backend.
 * Its mapping is pure: identity and context remain unchanged.
 *
 * This type deliberately has no Ktor imports. It is in jvmMain because
 * Solyton uses java.util.UUID. A portable representation is a separate
 * compatibility decision, not a silent substitution of the UUID type.
 */
data class Contextual<T>(
    val userId: UUID,
    val context: String,
    val data: T
)

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(userId, context, f(data))

/** Pure transformation of a Result, reusable outside a Ktor handler. */
class ResultTransform<S : Any, T : Any>(private val transform: (S) -> T) {
    fun apply(result: Result<S>): Result<T> = result.map(transform)
}

@Suppress("FunctionName")
fun <S : Any, T : Any> Transform(f: (S) -> T): ResultTransform<S, T> =
    ResultTransform(f)

/** Lift a pure transformation through both Result and Contextual. */
class ContextualTransform<S : Any, T : Any>(private val transform: (S) -> T) {
    fun apply(result: Result<Contextual<S>>): Result<Contextual<T>> =
        result.map { it.map(transform) }
}

@Suppress("FunctionName")
fun <S : Any, T : Any> TransformContextual(
    f: (S) -> T
): ContextualTransform<S, T> = ContextualTransform(f)
