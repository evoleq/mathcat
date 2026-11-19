package org.evoleq.mathcat.middleware

import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Storage
import org.evoleq.mathcat.ktorx.ApiClientResolver
import org.evoleq.mathcat.ktorx.Call
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
 * Standalone middleware DSL:
 *
 *   val response = Call(loginAction) runOn runtime
 *
 * Endpoint key is read from the action descriptor; the registry and selected
 * HttpClient are resolved at execution time, from a *single* Storage snapshot.
 * HTTP does not mutate Storage or silently invoke Action.writer.
 *
 * Reified payload types identify codecs in a manual or KSP-generated registry.
 */
@Suppress("FunctionName")
inline fun <State, reified Input : Any, reified Output : Any> Call(
    action: EndpointAction<State, Input, Output>
): MiddlewareEffect<State, Result<Contextual<Output>>> =
    MiddlewareEffect { runtime ->
        val state = runtime.storage.read()
        val apiClient = runtime.client.resolve(state)
        apiClient.Call<State, Input, Output>(
            action.endPoint,
            state,
            action.action.reader,
            action.pathParameters(state)
        )
    }
