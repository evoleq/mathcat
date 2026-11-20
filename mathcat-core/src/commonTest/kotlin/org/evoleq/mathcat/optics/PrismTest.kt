package org.evoleq.mathcat.optics

import org.evoleq.mathcat.core.optics.Prism
import org.evoleq.mathcat.core.optics.times
import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals

class PrismTest {
    private sealed interface Shape {
        data class Label(val text: String) : Shape
        data class Number(val value: Int) : Shape
    }

    private val label = Prism<Shape, Shape.Label>(
        match = { shape -> when (shape) {
            is Shape.Label -> Sum2.Left(shape)
            is Shape.Number -> Sum2.Right(shape)
        } },
        build = { it }
    )
    private data class NonEmpty(val value: String) {
        init { require(value.isNotEmpty()) }
    }

    private val nonempty = Prism<Shape.Label, NonEmpty>(
        match = { label ->
            if (label.text.isNotEmpty()) Sum2.Left(NonEmpty(label.text))
            else Sum2.Right(label)
        },
        build = { Shape.Label(it.value) }
    )

    @Test
    fun matchingAndBuildingAreSeparatePureOperations() {
        assertEquals(Sum2.Left(Shape.Label("a")), label.match(Shape.Label("a")))
        assertEquals(Sum2.Right(Shape.Number(3)), label.match(Shape.Number(3)))
        assertEquals(Shape.Label("a"), label.build(Shape.Label("a")))
    }

    @Test
    fun reviewAndPreviewLawsHoldForBothPrisms() {
        for (a in listOf(Shape.Label(""), Shape.Label("x"))) {
            assertEquals(Sum2.Left(a), label.match(label.build(a)))
            assertEquals(a, label.build(a))
        }
        for (a in listOf(NonEmpty("x"), NonEmpty("hello"))) {
            assertEquals(Sum2.Left(a), nonempty.match(nonempty.build(a)))
        }
        for (shape in listOf(Shape.Label("hello"), Shape.Number(2))) {
            when (val result = label.match(shape)) {
                is Sum2.Left -> assertEquals(shape, label.build(result.value))
                is Sum2.Right -> assertEquals(shape, result.value)
            }
        }
    }

    @Test
    fun compositionPreservesMissesAndEmbedsMatches() {
        val composed = label * nonempty
        assertEquals(Sum2.Left(NonEmpty("yes")), composed.match(Shape.Label("yes")))
        assertEquals(Sum2.Right(Shape.Label("")), composed.match(Shape.Label("")))
        assertEquals(Sum2.Right(Shape.Number(2)), composed.match(Shape.Number(2)))
        assertEquals(Shape.Label("yes"), composed.build(NonEmpty("yes")))
        assertEquals(Sum2.Left(NonEmpty("yes")), composed.match(composed.build(NonEmpty("yes"))))
    }
}
