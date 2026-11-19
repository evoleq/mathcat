package org.evoleq.mathcat.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.result.map
import org.evoleq.mathcat.serialization.ResultSerializer
import kotlin.reflect.KClass

/** Original Solyton Context header; no app-level context dependency. */
const val CONTEXT_HEADER: String = "Context"

/**
 * The Result-aware counterpart of requestEndpoint.
 * Keeps Solyton's Result<Contextual<T>> response and HTTP status semantics.
 *
 * Payload serializer may come from a manual or KSP-generated registry.
 * Never select a serializer from a response-provided simple class name.
 */
suspend fun <T : Any> HttpClient.requestResultEndpoint(
    api: Api,
    key: KClass<*>,
    baseUrl: String,
    responseSerializer: KSerializer<T>,
    pathParameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, List<String>> = emptyMap(),
    json: Json = Json,
    configure: HttpRequestBuilder.() -> Unit = {}
): Result<Contextual<T>> {
    val endpoint = requireNotNull(api[key]) { "Unknown endpoint key " + key }
    val path = resolvePathParameters(endpoint.url, pathParameters)
    val response = request(baseUrl.trimEnd('/') + "/" + path.trimStart('/')) {
        method = HttpMethod.parse(endpoint.method)
        url {
            for ((name, values) in queryParameters) {
                for (value in values) parameters.append(name, value)
            }
        }
        configure()
    }
    val responseBody = response.bodyAsText()
    val result = try {
        json.decodeFromString(ResultSerializer(responseSerializer), responseBody)
    } catch (error: SerializationException) {
        if (response.status.value in 200..299) throw error
        return Result.Failure.HttpStatusMessage(response.status.value, responseBody)
    }
    if (response.status.value !in 200..299) {
        return Result.Failure.HttpStatusMessage(
            response.status.value,
            when (result) {
                is Result.Failure.Message -> result.value
                else -> responseBody
            }
        )
    }
    val context = response.headers[CONTEXT_HEADER] ?: ""
    return result map { payload -> Contextual(context, payload) }
}

/** Send the declared API request body while expecting the legacy Result envelope. */
suspend fun <S : Any, T : Any> HttpClient.sendResultEndpoint(
    api: Api,
    key: KClass<*>,
    baseUrl: String,
    payload: S,
    requestSerializer: KSerializer<S>,
    responseSerializer: KSerializer<T>,
    pathParameters: Map<String, String> = emptyMap(),
    json: Json = Json
): Result<Contextual<T>> = requestResultEndpoint(
    api, key, baseUrl, responseSerializer, pathParameters = pathParameters, json = json
) {
    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    setBody(json.encodeToString(requestSerializer, payload))
}
