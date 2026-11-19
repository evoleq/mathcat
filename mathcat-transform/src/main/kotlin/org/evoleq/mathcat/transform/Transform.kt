package org.evoleq.mathcat.transform

import org.evoleq.mathcat.context.Contextual
import org.evoleq.mathcat.context.map
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.result.map

class ResultTransform<S : Any, T : Any>(private val transform: (S) -> T) {
    fun apply(result: Result<S>): Result<T> = result.map(transform)
}

@Suppress("FunctionName")
fun <S : Any, T : Any> Transform(f: (S) -> T): ResultTransform<S, T> =
    ResultTransform(f)

class ContextualTransform<S : Any, T : Any>(private val transform: (S) -> T) {
    fun apply(result: Result<Contextual<S>>): Result<Contextual<T>> =
        result.map { it.map(transform) }
}

@Suppress("FunctionName")
fun <S : Any, T : Any> TransformContextual(
    f: (S) -> T
): ContextualTransform<S, T> = ContextualTransform(f)
