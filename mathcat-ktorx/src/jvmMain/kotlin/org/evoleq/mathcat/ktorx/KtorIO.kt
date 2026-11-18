package org.evoleq.mathcat.ktorx

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.result.ExceptionTransforms
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.result.map
import org.evoleq.mathcat.serialization.ResultSerializer
import org.evoleq.mathcat.serialization.Serializers
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Missing context remains an application exception, mappable by a feature policy. */
class MissingContextHeader(val headerName: String) :
    IllegalArgumentException("Missing mandatory header: " + headerName)

/**
 * Auth identity lookup is injected for testing and alternative auth systems.
 * The default matches Solyton's JWTPrincipal-subject approach.
 */
suspend fun jwtUserId(call: ApplicationCall): Result<UUID> {
    val subject = call.principal<JWTPrincipal>()?.payload?.subject
        ?: return Result.Failure.HttpStatusMessage(401, "Invalid or expired token")
    return try {
        Result.Success(UUID.fromString(subject))
    } catch (e: IllegalArgumentException) {
        Result.Failure.HttpStatusMessage(401, "Invalid JWT subject")
    }
}

/**
 * Ktor-specific IO interpreter, parameterized by the application's own Base.
 * No request state, authentication or serializers are stored as globals.
 *
 * Usage inside a Route: with(io) {
 *   ReceiveContextual<CreateAuction>() * someBusinessAction *
 *       Respond<Auction> { transform() }
 * } runOn base
 */
class KtorIO<B>(
    val callOf: (B) -> ApplicationCall,
    val serializers: Serializers,
    val json: Json = Json,
    val contextHeader: String = CONTEXT_HEADER,
    val principal: suspend (ApplicationCall) -> Result<UUID> = ::jwtUserId,
    val transforms: ExceptionTransforms = ExceptionTransforms { }
) {
    @Suppress("FunctionName")
    fun <T : Any> Receive(serializer: KSerializer<T>): Action<B, Result<T>> =
        Action { base ->
            val received = try {
                Result.Success(json.decodeFromString(serializer, callOf(base).receiveText()))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.Failure.Exception(error)
            }
            received to base
        }

    @Suppress("FunctionName")
    inline fun <reified T : Any> Receive(): Action<B, Result<T>> =
        Receive(serializers.require(T::class))

    /** The original Receive(value) form, used for HEAD/GET-style workflows. */
    @Suppress("FunctionName")
    fun <T : Any> Receive(value: T): Action<B, Result<T>> =
        Action { base -> Result.Success(value) to base }

    private suspend fun <T : Any> contextual(
        base: B,
        decode: suspend (ApplicationCall) -> T
    ): Result<Contextual<T>> {
        val call = callOf(base)
        val identity = principal(call)
        if (identity is Result.Failure) return identity
        val userId = (identity as Result.Success).data
        val context = call.request.headers[contextHeader]
            ?: return Result.Failure.Exception(MissingContextHeader(contextHeader))
        return try {
            Result.Success(Contextual(userId, context, decode(call)))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Result.Failure.Exception(error)
        }
    }

    @Suppress("FunctionName")
    fun <T : Any> ReceiveContextual(serializer: KSerializer<T>):
        Action<B, Result<Contextual<T>>> = Action { base ->
        contextual(base) { call ->
            json.decodeFromString(serializer, call.receiveText())
        } to base
    }

    @Suppress("FunctionName")
    inline fun <reified T : Any> ReceiveContextual(): Action<B, Result<Contextual<T>>> =
        ReceiveContextual(serializers.require(T::class))

    /** Original query-parameter ReceiveContextual(params) form. */
    @Suppress("FunctionName")
    fun <T : Any> ReceiveContextual(params: (Parameters) -> T):
        Action<B, Result<Contextual<T>>> = Action { base ->
        contextual(base) { call -> params(call.request.queryParameters) } to base
    }

    /** Original injected ReceiveContextual(value) form. */
    @Suppress("FunctionName")
    fun <T : Any> ReceiveContextual(value: T): Action<B, Result<Contextual<T>>> =
        Action { base -> contextual(base) { value } to base }

    @Suppress("FunctionName")
    fun <S : Any, T : Any> Transform(f: (S) -> T):
        KlAction<B, Result<S>, Result<T>> =
        { input -> Action { base -> (input map f) to base } }

    @Suppress("FunctionName")
    fun <S : Any, T : Any> TransformContextual(f: (S) -> T):
        KlAction<B, Result<Contextual<S>>, Result<Contextual<T>>> =
        { input -> Action { base -> (input map { contextual -> contextual map f }) to base } }

    /**
     * Keep Solyton's Respond<T> { transform() } extension-lambda shape.
     * When omitted, transform is provided by feature-composed ExceptionTransforms.
     */
    @Suppress("FunctionName")
    fun <T : Any> Respond(
        serializer: KSerializer<T>,
        context: String = "",
        transformException: Result.Failure.Exception.() ->
            Pair<HttpStatusCode, Result.Failure.Message> = {
                val mapped = transforms.transform(this)
                HttpStatusCode.fromValue(mapped.statusCode) to mapped.message
            }
    ): KlAction<B, Result<T>, Unit> = { result ->
        Action { base ->
            val call = callOf(base)
            if (context.isNotEmpty() && context != call.request.headers[contextHeader]) {
                call.response.header(contextHeader, context)
            }
            val response: Pair<HttpStatusCode, Result<T>> = when (result) {
                is Result.Success -> HttpStatusCode.OK to result
                is Result.Failure.Message -> HttpStatusCode.InternalServerError to result
                is Result.Failure.HttpStatusMessage ->
                    HttpStatusCode.fromValue(result.statusCode) to
                        Result.Failure.Message(result.value)
                is Result.Failure.Exception -> {
                    val (code, message) = result.transformException()
                    code to message
                }
            }
            val (status, envelope) = response
            call.respondText(
                text = json.encodeToString(ResultSerializer(serializer), envelope),
                contentType = ContentType.Application.Json,
                status = status
            )
            Unit to base
        }
    }

    @Suppress("FunctionName")
    inline fun <reified T : Any> Respond(
        context: String = "",
        noinline transformException: Result.Failure.Exception.() ->
            Pair<HttpStatusCode, Result.Failure.Message> = {
                val mapped = transforms.transform(this)
                HttpStatusCode.fromValue(mapped.statusCode) to mapped.message
            }
    ): KlAction<B, Result<T>, Unit> =
        Respond(serializers.require(T::class), context, transformException)
}
