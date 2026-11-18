package org.evoleq.mathcat.types

/**
 * A small, first-order *model* of type expressions. This is an AST of values;
 * it does not change Kotlin's type checker or provide higher-kinded types.
 */
sealed interface TypeExpr {
    data class Atom(val name: String) : TypeExpr
    data object Zero : TypeExpr
    data object One : TypeExpr
    data class Sum(val left: TypeExpr, val right: TypeExpr) : TypeExpr
    data class Product(val left: TypeExpr, val right: TypeExpr) : TypeExpr
    data class Apply(val typeConstructor: TypeConstructor, val argument: TypeExpr) : TypeExpr
}

/**
 * Unary type constructors, corresponding to the proposed kind Type -> Type.
 * The Kotlin representation is syntax data, not a constructor variable F<_>.
 */
sealed interface TypeConstructor {
    data object Identity : TypeConstructor
    data class Named(val symbol: String) : TypeConstructor
    data class Constant(val value: TypeExpr) : TypeConstructor
    data class Compose(val outer: TypeConstructor, val inner: TypeConstructor) : TypeConstructor
    data class Sum(val left: TypeConstructor, val right: TypeConstructor) : TypeConstructor
    data class Product(val left: TypeConstructor, val right: TypeConstructor) : TypeConstructor
}

fun TypeConstructor.at(argument: TypeExpr): TypeExpr =
    TypeExpr.Apply(this, argument).normalize()

/**
 * Only the expressly defined reductions are performed.
 * Commutativity and distributivity are isomorphisms, NOT type equalities.
 */
fun TypeExpr.normalize(): TypeExpr = when (this) {
    is TypeExpr.Atom, TypeExpr.Zero, TypeExpr.One -> this
    is TypeExpr.Sum -> TypeExpr.Sum(left.normalize(), right.normalize())
    is TypeExpr.Product -> TypeExpr.Product(left.normalize(), right.normalize())
    is TypeExpr.Apply -> when (val ctor = typeConstructor) {
        TypeConstructor.Identity -> argument.normalize()
        is TypeConstructor.Named -> TypeExpr.Apply(ctor, argument.normalize())
        is TypeConstructor.Constant -> ctor.value.normalize()
        is TypeConstructor.Compose ->
            TypeExpr.Apply(ctor.outer, TypeExpr.Apply(ctor.inner, argument)).normalize()
        is TypeConstructor.Sum -> TypeExpr.Sum(
            TypeExpr.Apply(ctor.left, argument),
            TypeExpr.Apply(ctor.right, argument)
        ).normalize()
        is TypeConstructor.Product -> TypeExpr.Product(
            TypeExpr.Apply(ctor.left, argument),
            TypeExpr.Apply(ctor.right, argument)
        ).normalize()
    }
}
