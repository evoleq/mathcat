package evoleq.toolkit.types

/**
 * Ordinary Kotlin representations of a tagged coproduct and a product.
 * These are NOT built-in Kotlin type operators.
 */
sealed interface Sum2<out A, out B> {
    data class Left<A>(val value: A) : Sum2<A, Nothing>
    data class Right<B>(val value: B) : Sum2<Nothing, B>
}

data class Product2<out A, out B>(val first: A, val second: B)

fun <A, B, C, D> Sum2<A, B>.bimap(
    left: (A) -> C,
    right: (B) -> D
): Sum2<C, D> = when (this) {
    is Sum2.Left -> Sum2.Left(left(value))
    is Sum2.Right -> Sum2.Right(right(value))
}

fun <A, B, C, D> Product2<A, B>.bimap(
    first: (A) -> C,
    second: (B) -> D
): Product2<C, D> = Product2(first(this.first), second(this.second))

/** Explicit distributivity isomorphism: (A + B) × C -> (A × C) + (B × C). */
fun <A, B, C> distribute(
    value: Product2<Sum2<A, B>, C>
): Sum2<Product2<A, C>, Product2<B, C>> = when (val sum = value.first) {
    is Sum2.Left -> Sum2.Left(Product2(sum.value, value.second))
    is Sum2.Right -> Sum2.Right(Product2(sum.value, value.second))
}

/** Inverse isomorphism; unlike type normalization, this is an actual transformation. */
fun <A, B, C> factor(
    value: Sum2<Product2<A, C>, Product2<B, C>>
): Product2<Sum2<A, B>, C> = when (value) {
    is Sum2.Left -> Product2(Sum2.Left(value.value.first), value.value.second)
    is Sum2.Right -> Product2(Sum2.Right(value.value.first), value.value.second)
}
