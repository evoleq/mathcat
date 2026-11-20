package evoleq.toolkit.types

import kotlin.test.Test
import kotlin.test.assertEquals

class TypeAlgebraTest {
    private val f = TypeConstructor.Named("F")
    private val g = TypeConstructor.Named("G")
    private val h = TypeConstructor.Named("H")
    private val a = TypeExpr.Atom("A")

    @Test
    fun identityConstructorActsAsIdentity() {
        assertEquals(a, TypeConstructor.Identity.at(a))
    }

    @Test
    fun identityIsNeutralForConstructorComposition() {
        assertEquals(f.at(a), TypeConstructor.Compose(TypeConstructor.Identity, f).at(a))
        assertEquals(f.at(a), TypeConstructor.Compose(f, TypeConstructor.Identity).at(a))
    }

    @Test
    fun constructorCompositionIsAssociativeOnApplications() {
        val left = TypeConstructor.Compose(TypeConstructor.Compose(f, g), h).at(a)
        val right = TypeConstructor.Compose(f, TypeConstructor.Compose(g, h)).at(a)
        assertEquals(left, right)
    }

    @Test
    fun pointwiseSumReducesToSumOfApplications() {
        assertEquals(TypeExpr.Sum(f.at(a), g.at(a)), TypeConstructor.Sum(f, g).at(a))
    }

    @Test
    fun pointwiseProductReducesToProductOfApplications() {
        assertEquals(TypeExpr.Product(f.at(a), g.at(a)), TypeConstructor.Product(f, g).at(a))
    }

    @Test
    fun constantConstructorIgnoresTheArgument() {
        assertEquals(TypeExpr.One, TypeConstructor.Constant(TypeExpr.One).at(a))
    }

    @Test
    fun sumTagsRemainDistinctEvenWhenPayloadTypesAreEqual() {
        val left: Sum2<Int, Int> = Sum2.Left(7)
        val right: Sum2<Int, Int> = Sum2.Right(7)
        kotlin.test.assertNotEquals(left, right)
    }

    @Test
    fun distributivityHasAnExplicitInverseForBothBranches() {
        val left: Product2<Sum2<Int, String>, Boolean> =
            Product2(Sum2.Left(7), true)
        val right: Product2<Sum2<Int, String>, Boolean> =
            Product2(Sum2.Right("r"), false)
        assertEquals(left, factor(distribute(left)))
        assertEquals(right, factor(distribute(right)))
    }

    @Test
    fun sumAndProductBimapPreserveIdentities() {
        val left: Sum2<Int, String> = Sum2.Left(3)
        val product = Product2(3, "a")
        assertEquals(left, left.bimap({ it }, { it }))
        assertEquals(product, product.bimap({ it }, { it }))
    }

    @Test
    fun sumAndProductBimapPreserveComposition() {
        val sum: Sum2<Int, String> = Sum2.Right("a")
        val product = Product2(3, "a")
        assertEquals(
            sum.bimap({ it + 1 }, { it + "b" }).bimap({ it * 2 }, { it.length }),
            sum.bimap({ (it + 1) * 2 }, { (it + "b").length })
        )
        assertEquals(
            product.bimap({ it + 1 }, { it + "b" }).bimap({ it * 2 }, { it.length }),
            product.bimap({ (it + 1) * 2 }, { (it + "b").length })
        )
    }
}
