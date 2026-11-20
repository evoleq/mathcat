package evoleq.toolkit.ktorx.context.data

/**
 * Client-side Contextual value, preserving the Solyton shape.
 * Server-side Contextual also carries the authenticated user ID.
 */
data class Contextual<out T>(
    val context: String,
    val data: T
)

infix fun <S, T> Contextual<S>.map(f: (S) -> T): Contextual<T> =
    Contextual(context, f(data))
