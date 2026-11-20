package evoleq.toolkit.middleware

import evoleq.toolkit.actions.ActionDispatcher
import evoleq.toolkit.actions.ActionEnvelope
import evoleq.toolkit.core.storage.writeWith
import evoleq.toolkit.ktorx.context.data.Contextual
import evoleq.toolkit.result.Result
import kotlin.coroutines.cancellation.CancellationException

/**
 * One snapshot is captured at the start of a pipeline. Its Reader and
 * transport selection see the same state even if asynchronous work follows.
 * Storage writes use the *latest* value at dispatch time (non-atomic RMW).
 */
class MiddlewareExecution<S>(
    val runtime: MiddlewareRuntime<S>,
    val state: S
)

/** Typed suspendable Kleisli step; unlike the pure core State it can do IO. */
fun interface MiddlewareStep<S, A, B> {
    suspend fun execute(input: A, context: MiddlewareExecution<S>): B
}

/** Left-to-right Kleisli composition of middleware steps. */
operator fun <S, A, B, C> MiddlewareStep<S, A, B>.times(
    next: MiddlewareStep<S, B, C>
): MiddlewareStep<S, A, C> = MiddlewareStep { value, context ->
    next.execute(this.execute(value, context), context)
}

/**
 * Execute Unit-input middleware without leaking a gateway at its call site.
 * One state snapshot is read for the entire pipeline.
 */
suspend infix fun <S, T> MiddlewareStep<S, Unit, T>.runOn(
    runtime: MiddlewareRuntime<S>
): T = execute(Unit, MiddlewareExecution(runtime, runtime.storage.read()))

/** Optional explicit-input execution, sharing one snapshot across all steps. */
suspend fun <S, A, T> MiddlewareStep<S, A, T>.runOn(
    runtime: MiddlewareRuntime<S>,
    input: A
): T = execute(input, MiddlewareExecution(runtime, runtime.storage.read()))

@Suppress("FunctionName")
fun <S, I : Any, O : Any> Read(
    action: EndpointAction<S, I, O>
): MiddlewareStep<S, Unit, I> = MiddlewareStep { _, context ->
    action.action.reader(context.state)
}

/**
 * Error policy transforms one Failure to another. Never transforms a Success
 * or performs hidden dispatch. Applications may inject custom error sinks.
 */
fun interface FailurePolicy<S> {
    suspend fun handle(error: Result.Failure, state: S): Result.Failure
}

@Suppress("FunctionName")
fun <S, I : Any, O : Any> OnError(
    action: EndpointAction<S, I, O>,
    policy: FailurePolicy<S> = FailurePolicy { failure, _ -> failure }
): MiddlewareStep<S, Result<Contextual<O>>, Result<Contextual<O>>> =
    MiddlewareStep { result, context ->
        // The action parameter records the stage's binding; policies are
        // application-owned and cannot depend on Solyton UI classes.
        @Suppress("UNUSED_VARIABLE")
        val actionName = action.action.name
        when (result) {
            is Result.Success -> result
            is Result.Failure -> policy.handle(result, context.state)
        }
    }

/**
 * On success, propagate optional context and write the decoded response.
 * Failure stays unchanged and MUST NOT trigger success writers.
 */
@Suppress("FunctionName")
fun <S, I : Any, O : Any> Dispatch(
    action: EndpointAction<S, I, O>,
    onContext: suspend (String, MiddlewareRuntime<S>) -> Unit = { _, _ -> }
): MiddlewareStep<S, Result<Contextual<O>>, Result<O>> =
    MiddlewareStep { response, execution ->
        when (response) {
            is Result.Failure -> response
            is Result.Success -> try {
                onContext(response.data.context, execution.runtime)
                execution.runtime.storage.writeWith(action.action.writer, response.data.data)
                Result.Success(response.data.data)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.Failure.Exception(error)
            }
        }
    }

/**
 * A general success reaction: apply the action's optional onSuccess Writer
 * and a supplied feature-local effect, sequentially (structured suspension).
 * No process lifecycle, permission cases, background Job or UI modal is owned.
 */
@Suppress("FunctionName")
fun <S, I : Any, O : Any> React(
    action: EndpointAction<S, I, O>,
    onSuccess: suspend (O, MiddlewareRuntime<S>) -> Unit = { _, _ -> }
): MiddlewareStep<S, Result<O>, Result<O>> =
    MiddlewareStep { response, execution ->
        when (response) {
            is Result.Failure -> response
            is Result.Success -> try {
                action.action.onSuccess?.let { hook ->
                    execution.runtime.storage.writeWith(hook, response.data)
                }
                onSuccess(response.data, execution.runtime)
                response
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.Failure.Exception(error)
            }
        }
    }

/**
 * Follow-up action dispatch, not process management. Success is required;
 * deferred suppliers run only on success, in declared order, as part of the
 * caller's suspension scope. Envelope metadata is forwarded unchanged.
 */
@Suppress("FunctionName")
fun <S, I, O : Any> ReactEnvelope(
    envelope: ActionEnvelope<S, I, O>,
    dispatcher: ActionDispatcher<S>
): MiddlewareStep<S, Result<O>, Result<O>> =
    MiddlewareStep { response, _ ->
        when (response) {
            is Result.Failure -> response
            is Result.Success -> try {
                for (next in envelope.next) dispatcher.dispatchEnvelope(next)
                for (nextLazy in envelope.nextLazy) {
                    dispatcher.dispatchEnvelope(nextLazy())
                }
                response
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.Failure.Exception(error)
            }
        }
    }

/** Opt-in observer, no browser/console/logging implementation in Toolkit. */
@Suppress("FunctionName")
fun <S, T> Debug(
    observe: suspend (T) -> Unit
): MiddlewareStep<S, T, T> = MiddlewareStep { value, _ ->
    observe(value)
    value
}

/**
 * The common frontend middleware recipe. Every stage is independently
 * replaceable, and process registration is deliberately left out.
 *
 * Read * Call * OnError * Dispatch * React
 */
@Suppress("FunctionName")
inline fun <S, reified I : Any, reified O : Any> ProcessAction(
    action: EndpointAction<S, I, O>,
    policy: FailurePolicy<S> = FailurePolicy { failure, _ -> failure },
    noinline onContext: suspend (String, MiddlewareRuntime<S>) -> Unit = { _, _ -> },
    noinline onSuccess: suspend (O, MiddlewareRuntime<S>) -> Unit = { _, _ -> }
): MiddlewareStep<S, Unit, Result<O>> =
    ProcessAction(action, Call(action), policy, onContext, onSuccess)

/** Fully explicit form accepts an application-supplied Call interpreter. */
@Suppress("FunctionName")
fun <S, I : Any, O : Any> ProcessAction(
    action: EndpointAction<S, I, O>,
    call: MiddlewareCall<S, I, O>,
    policy: FailurePolicy<S> = FailurePolicy { failure, _ -> failure },
    onContext: suspend (String, MiddlewareRuntime<S>) -> Unit = { _, _ -> },
    onSuccess: suspend (O, MiddlewareRuntime<S>) -> Unit = { _, _ -> }
): MiddlewareStep<S, Unit, Result<O>> =
    Read(action) * call * OnError(action, policy) *
        Dispatch(action, onContext) * React(action, onSuccess)

/** More concise, separately bound Call(action) is passed in explicitly
 * to avoid requiring reified type arguments during stage composition. */
