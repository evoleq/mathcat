package org.evoleq.mathcat.result

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ResultMathTest {
    @Test fun successMapsAndBinds() {
        assertEquals(Result.Success(8), Result.Success(4) map { it * 2 })
        assertEquals(Result.Success(5), Result.Success(4) bind { Result.Success(it + 1) })
    }

    @Test fun failureShortCircuitsAndKeepsItsValue() {
        val failure = Result.Failure.Message("not permitted")
        assertSame(failure, failure.map { "ignored" })
        assertSame(failure, failure.bind { Result.Success("ignored") })
    }

    @Test fun nestedResultMultiplies() {
        assertEquals(Result.Success("ok"), Result.Success(Result.Success("ok")).multiply())
    }

    @Test fun oldConstructorsAndApplicativeAreAvailable() {
        assertEquals(Result.Success(5), Result.Return(5))
        assertEquals(Result.Success(5), Result(5))
        val twice: Result<(Int) -> Int> = Result.Success { it * 2 }
        assertEquals(Result.Success(6), twice.apply()(Result.Success(3)))
    }

    @Test fun ordinaryExceptionsAreRepresentedAsFailures() {
        val result = Result.Success(1).map<Int, Int> { error("boom") }
        assertIs<Result.Failure.Exception>(result)
        assertEquals("boom", result.value.message)
    }

    @Test fun cancellationExceptionsAreNeverSwallowed() {
        assertFailsWith<CancellationException> {
            Result.Success(1).map<Int, Int> { throw CancellationException("cancel") }
        }
        assertFailsWith<CancellationException> {
            runImmediate {
                Result.Success(1).mapSuspend<Int, Int> {
                    throw CancellationException("cancel suspend")
                }
            }
        }
    }

    @Test fun suspendOperationsRetainSuccessAndFailureSemantics() {
        val result = runImmediate {
            Result.Success(3) bindSuspend { Result.Success(it + 4) }
        }
        assertEquals(Result.Success(7), result)
        val failure = Result.Failure.HttpStatusMessage(403, "Forbidden")
        assertSame(failure, runImmediate { failure mapSuspend { 20 } })
    }
}

/** Small portable runner for immediately completing suspend lambdas in tests. */
private fun <T> runImmediate(block: suspend () -> T): T {
    var completed: kotlin.Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: kotlin.Result<T>) {
            completed = result
        }
    })
    return requireNotNull(completed) {
        "Test coroutine unexpectedly suspended"
    }.getOrThrow()
}
