package evoleq.toolkit.ktorx

/**
 * Ktorx retains its public Contextual name, but delegates the JVM server value
 * to the single canonical, Ktor-independent toolkit-context implementation.
 * The portable client Contextual deliberately has a different (identity-free) shape.
 */
typealias Contextual<T> = evoleq.toolkit.context.Contextual<T>

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(userId, context, f(data))
