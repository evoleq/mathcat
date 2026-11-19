package org.evoleq.mathcat.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Portable result of an operation. This intentionally retains the Evoleq
 * Success / Failure vocabulary without depending on Ktor's HttpStatusCode.
 */
sealed class Result<out T : Any> {
    data class Success<out T : Any>(val data: T) : Result<T>()

    sealed class Failure : Result<Nothing>() {
        data class Message(val value: String) : Failure()
        data class Exception(val value: Throwable) : Failure()
        /** The HTTP adapter translates this numerical code to Ktor's HttpStatusCode. */
        data class HttpStatusMessage(val statusCode: Int, val value: String) : Failure()
    }

    companion object
}

/** The original Evoleq names and functor/monad vocabulary are retained. */
@Suppress("FunctionName")
fun <T : Any> Result.Companion.Return(value: T): Result<T> = Result.Success(value)

operator fun <T : Any> Result.Companion.invoke(value: T): Result<T> =
    Result.Success(value)

infix fun <S : Any, T : Any> Result<S>.map(f: (S) -> T): Result<T> =
    when (this) {
        is Result.Success -> try {
            Result.Success(f(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Failure.Exception(e)
        }
        is Result.Failure -> this
    }

fun <T : Any> Result<Result<T>>.multiply(): Result<T> = when (this) {
    is Result.Success -> data
    is Result.Failure -> this
}

infix fun <S : Any, T : Any> Result<S>.bind(f: (S) -> Result<T>): Result<T> =
    (this map f).multiply()

fun <S : Any, T : Any> Result<(S) -> T>.apply(): (Result<S>) -> Result<T> = {
    value -> this bind { f -> value map f }
}

fun Result<(Unit) -> Unit>.dispatch(): Result<Unit> = map { it(Unit) }

/**
 * Preserves the suspend operation names without introducing a global Flow.
 * Cancellation is rethrown, not converted to a normal Failure.
 */
suspend infix fun <S : Any, T : Any> Result<S>.mapSuspend(f: suspend (S) -> T): Result<T> =
    when (this) {
        is Result.Success -> try {
            Result.Success(f(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Failure.Exception(e)
        }
        is Result.Failure -> this
    }

suspend fun <T : Any> Result<Result<T>>.multiplySuspend(): Result<T> =
    multiply()

suspend infix fun <S : Any, T : Any> Result<S>.bindSuspend(
    f: suspend (S) -> Result<T>
): Result<T> = (this mapSuspend f).multiplySuspend()

suspend fun <S : Any, T : Any> Result<suspend (S) -> T>.applySuspend():
    suspend (Result<S>) -> Result<T> = {
        value -> this bindSuspend { f -> value mapSuspend f }
    }

suspend fun Result<suspend (Unit) -> Unit>.dispatchSuspend(): Result<Unit> =
    mapSuspend { it(Unit) }
