package org.evoleq.mathcat.ktorx

/**
 * Ktorx retains its public Contextual name, but delegates the JVM server value
 * to the single canonical, Ktor-independent mathcat-context implementation.
 * The portable client Contextual deliberately has a different (identity-free) shape.
 */
typealias Contextual<T> = org.evoleq.mathcat.context.Contextual<T>

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(userId, context, f(data))
