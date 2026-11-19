package org.evoleq.mathcat.middleware

import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Storage
import org.evoleq.mathcat.ktorx.ApiClientResolver
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import kotlin.reflect.KClass

/**
 * Binds a *transport-independent* Action to its API endpoint once, at the
 * composition root / Action declaration. Call(action) needs no repeated keys.
 *
 * EndpointAction is an opt-in middleware descriptor. The pure Action remains
 * reusable by non-HTTP interpreters and does not depend on Ktor or codecs.
 */
data class EndpointAction<State, Input : Any, Output : Any>(
    val action: Action<State, Input, Output>,
    val endPoint: KClass<*>,
    val pathParameters: Reader<State, Map<String, String>> = Reader { emptyMap() }
)

/** Declare an Action's endpoint once; no endpoint key at the Call site. */
fun <State, Input : Any, Output : Any> Action<State, Input, Output>.at(
    endpoint: KClass<*>,
    pathParameters: Reader<State, Map<String, String>> = Reader { emptyMap() }
): EndpointAction<State, Input, Output> =
    EndpointAction(action = this, endPoint = endpoint, pathParameters = pathParameters)

/**
 * Effectful middleware runtime. It owns neither the storage lifetime nor
 * the HTTP client policy; the application provides both.
 */
class MiddlewareRuntime<State>(
    val storage: Storage<State>,
    val client: ApiClientResolver<State>
)

/** A suspendable Reader-like effect, intentionally NOT the pure core State. */
class MiddlewareEffect<State, out Value>(
    private val execute: suspend (MiddlewareRuntime<State>) -> Value
) {
    suspend infix fun runOn(runtime: MiddlewareRuntime<State>): Value = execute(runtime)

    fun <Next> map(transform: suspend (Value) -> Next): MiddlewareEffect<State, Next> =
        MiddlewareEffect { runtime -> transform(execute(runtime)) }

    fun <Next> flatMap(
        transform: suspend (Value) -> MiddlewareEffect<State, Next>
    ): MiddlewareEffect<State, Next> =
        MiddlewareEffect { runtime -> transform(execute(runtime)).runOn(runtime) }
}

/**
 * Standalone effectful call which also acts as an A -> Result<Contextual<B>>
 * middleware step. The standalone runOn reads the current state once;
 * a composed Read * Call pipeline shares the *same* initial snapshot.
 */
class MiddlewareCall<State, Input : Any, Output : Any>(
    val action: EndpointAction<State, Input, Output>,
    private val send: suspend (Input, MiddlewareExecution<State>) -> Result<Contextual<Output>>
) : MiddlewareStep<State, Input, Result<Contextual<Output>>> {
    override suspend fun execute(
        input: Input,
        execution: MiddlewareExecution<State>
    ): Result<Contextual<Output>> = send(input, execution)

    suspend infix fun runOn(runtime: MiddlewareRuntime<State>): Result<Contextual<Output>> {
        val state = runtime.storage.read()
        return execute(action.action.reader(state), MiddlewareExecution(runtime, state))
    }

    fun <Next> map(transform: suspend (Result<Contextual<Output>>) -> Next):
        MiddlewareEffect<State, Next> = MiddlewareEffect { runtime ->
        transform(this.runOn(runtime))
    }
}

/**
 * A free-standing Call(action), not a method on a gateway.
 *
 * No extra endpoint key, reader or serializer at the call site. The pure
 * Action reader is evaluated only by standalone runOn; in a composed pipeline
 * its input is received directly from the preceding Read stage.
 */
@Suppress("FunctionName")
inline fun <State, reified Input : Any, reified Output : Any> Call(
    action: EndpointAction<State, Input, Output>
): MiddlewareCall<State, Input, Output> =
    MiddlewareCall(action) { input, execution ->
        val apiClient = execution.runtime.client.resolve(execution.state)
        apiClient.executeAction<State, Input, Output>(
            action.endPoint,
            execution.state,
            Reader { input },
            action.pathParameters(execution.state)
        )
    }
