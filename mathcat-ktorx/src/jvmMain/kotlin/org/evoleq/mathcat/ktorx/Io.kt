package org.evoleq.mathcat.ktorx

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.result.Result
import kotlin.coroutines.cancellation.CancellationException
import java.util.UUID

/**
 * Effectful counterpart of the pure core State. The application supplies E,
 * rather than forcing a Database/DI model into MathCat.
 */
data class RequestBase<E>(
    val call: ApplicationCall,
    val environment: E
)

/** Suspendable state computation scoped to exactly one request. */
class KtorAction<E, out T>(
    private val action: suspend (RequestBase<E>) -> Pair<T, RequestBase<E>>
) {
    suspend fun execute(base: RequestBase<E>): Pair<T, RequestBase<E>> = action(base)
    suspend infix fun runOn(base: RequestBase<E>): T = action(base).first
}

fun <E, S : Any, T : Any> KtorAction<E, Result<S>>.times(
    transform: ResultTransform<S, T>
): KtorAction<E, Result<T>> = KtorAction { base ->
    val (value, next) = execute(base)
    transform.apply(value) to next
}

fun <E, S : Any, T : Any> KtorAction<E, Result<Contextual<S>>>.times(
    transform: ContextualTransform<S, T>
): KtorAction<E, Result<Contextual<T>>> = KtorAction { base ->
    val (value, next) = execute(base)
    transform.apply(value) to next
}

private suspend fun <T> attempt(operation: suspend () -> T): Result<T>
    where T : Any = try {
    Result.Success(operation())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.Failure.Exception(e)
}

/** Solyton's Receive, with an explicit decoder sourced from any registry. */
@Suppress("FunctionName")
fun <E, T : Any> Receive(
    serializer: KSerializer<T>,
    json: Json = Json
): KtorAction<E, Result<T>> = KtorAction { base ->
    attempt {
        json.decodeFromString(serializer, base.call.receiveText())
    } to base
}

/** Solyton's injected-value Receive overload (no body needed). */
@Suppress("FunctionName")
fun <E, T : Any> Receive(value: T): KtorAction<E, Result<T>> =
    KtorAction { base -> Result.Success(value) to base }

/**
 * Authentication is an application-supplied adapter, not hard-coded JWT/DB
 * policy. This keeps the original userId/context/data shape and lets Solyton
 * supply its existing principal extraction and context-header convention.
 */
data class RequestIdentity(
    val userId: UUID,
    val context: String
)

/** Body-based ReceiveContextual; failed authentication short-circuits. */
@Suppress("FunctionName")
fun <E, T : Any> ReceiveContextual(
    serializer: KSerializer<T>,
    identity: suspend (ApplicationCall) -> Result<RequestIdentity>,
    json: Json = Json
): KtorAction<E, Result<Contextual<T>>> = KtorAction { base ->
    val authenticated = identity(base.call)
    val result: Result<Contextual<T>> = when (authenticated) {
        is Result.Success -> attempt {
            Contextual(
                authenticated.data.userId,
                authenticated.data.context,
                json.decodeFromString(serializer, base.call.receiveText())
            )
        }
        is Result.Failure -> authenticated
    }
    result to base
}

/** Query-parameter-based overload, matching Solyton's params variant. */
@Suppress("FunctionName")
fun <E, T : Any> ReceiveContextual(
    params: (Parameters) -> T,
    identity: suspend (ApplicationCall) -> Result<RequestIdentity>
): KtorAction<E, Result<Contextual<T>>> = KtorAction { base ->
    val authenticated = identity(base.call)
    val result: Result<Contextual<T>> = when (authenticated) {
        is Result.Success -> attempt {
            Contextual(
                authenticated.data.userId,
                authenticated.data.context,
                params(base.call.request.queryParameters)
            )
        }
        is Result.Failure -> authenticated
    }
    result to base
}

/** Context-only payload, for actions without request body or query DTO. */
@Suppress("FunctionName")
fun <E> Context(
    identity: suspend (ApplicationCall) -> Result<RequestIdentity>
): KtorAction<E, Result<Contextual<Unit>>> = KtorAction { base ->
    val authenticated = identity(base.call)
    val result: Result<Contextual<Unit>> = when (authenticated) {
        is Result.Success ->
            Result.Success(Contextual(
                authenticated.data.userId, authenticated.data.context, Unit
            ))
        is Result.Failure -> authenticated
    }
    result to base
}

/**
 * Default Solyton-compatible HTTP cases; customize exception translation.
 * The Result codec is explicit to allow the manual or KSP registry.
 */
data class ResponsePolicy(
    val contextHeader: String = "Context",
    val defaultFailureStatus: HttpStatusCode = HttpStatusCode.InternalServerError,
    val onException: (Result.Failure.Exception) ->
        Pair<HttpStatusCode, Result.Failure.Message> = {
            HttpStatusCode.InternalServerError to Result.Failure.Message(
                it.value.message ?: "No message provided"
            )
        }
)

/**
 * Respond is a terminal Kleisli step. It keeps the caller's request base,
 * converts the HTTP status at the boundary and emits Result JSON using the
 * supplied serializer, not a hidden global mutable registry.
 */
class Responder<T : Any>(
    private val serializer: KSerializer<Result<T>>,
    private val json: Json,
    private val policy: ResponsePolicy,
    private val context: String?
) {
    suspend fun <E> respond(value: Result<T>, base: RequestBase<E>): RequestBase<E> {
        val (status, actual): Pair<HttpStatusCode, Result<T>> = when (value) {
            is Result.Success -> HttpStatusCode.OK to value
            is Result.Failure.HttpStatusMessage ->
                HttpStatusCode.fromValue(value.statusCode) to value
            is Result.Failure.Message -> policy.defaultFailureStatus to value
            is Result.Failure.Exception -> {
                val (status, message) = policy.onException(value)
                status to message
            }
        }
        if (context != null && context != base.call.request.headers[policy.contextHeader]) {
            base.call.response.headers.append(policy.contextHeader, context)
        }
        base.call.respondText(
            text = json.encodeToString(serializer, actual),
            contentType = ContentType.Application.Json,
            status = status
        )
        return base
    }
}

@Suppress("FunctionName")
fun <T : Any> Respond(
    serializer: KSerializer<Result<T>>,
    context: String? = null,
    json: Json = Json,
    policy: ResponsePolicy = ResponsePolicy()
): Responder<T> = Responder(serializer, json, policy, context)

fun <E, T : Any> KtorAction<E, Result<T>>.times(
    responder: Responder<T>
): KtorAction<E, Unit> = KtorAction { base ->
    val (value, next) = execute(base)
    Unit to responder.respond(value, next)
}

/** Additional arbitrary effectful stage, preserving the action's context. */
class KtorStep<E, S, T>(
    val execute: suspend (S, RequestBase<E>) -> Pair<T, RequestBase<E>>
)

fun <E, S, T> KtorAction<E, S>.times(
    step: KtorStep<E, S, T>
): KtorAction<E, T> = KtorAction { base ->
    val (value, next) = execute(base)
    step.execute(value, next)
}
