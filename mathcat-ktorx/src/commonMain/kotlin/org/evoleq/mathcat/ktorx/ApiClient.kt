package org.evoleq.mathcat.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.Parameters
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.Serializers
import kotlin.reflect.KClass

/**
 * A frontend application's HTTP Boundary. The caller chooses an HttpClient
 * (e.g. logged-in vs anonymous), a base URL and a composed serializers registry.
 *
 * No global serializer map, hardcoded environment or persistence is introduced.
 */
class ApiClient(
    val client: HttpClient,
    val api: Api,
    val baseUrl: String,
    val serializers: Serializers,
    val json: Json = Json
) {
    private fun endpoint(key: KClass<*>) =
        requireNotNull(api[key]) { "Unknown API endpoint key: " + key }

    /** Resolve the expected result type before any network request. */
    suspend inline fun <reified T : Any> request(
        key: KClass<*>,
        pathParameters: Map<String, String> = emptyMap(),
        queryParameters: Map<String, List<String>> = emptyMap(),
        noinline configure: HttpRequestBuilder.() -> Unit = {}
    ): Result<Contextual<T>> {
        val declared = requireNotNull(api[key]) { "Unknown API endpoint key: " + key }
        require(declared.responseType == T::class) {
            "Endpoint " + key + " declares response " + declared.responseType +
                ", not " + T::class
        }
        return client.requestResultEndpoint(
            api, key, baseUrl, serializers.require(T::class),
            pathParameters = pathParameters, queryParameters = queryParameters,
            json = json, configure = configure
        )
    }

    /** Encodes input and decodes Result without serializers at each call site. */
    suspend inline fun <reified S : Any, reified T : Any> send(
        key: KClass<*>,
        payload: S,
        pathParameters: Map<String, String> = emptyMap()
    ): Result<Contextual<T>> {
        val declared = requireNotNull(api[key]) { "Unknown API endpoint key: " + key }
        require(declared.requestType == S::class && declared.responseType == T::class) {
            "Endpoint " + key + " declares " + declared.requestType + " -> " +
                declared.responseType + ", not " + S::class + " -> " + T::class
        }
        return client.sendResultEndpoint(
            api, key, baseUrl, payload,
            serializers.require(S::class), serializers.require(T::class),
            pathParameters = pathParameters, json = json
        )
    }

    /**
     * Interpret an Action's pure Reader at the HTTP boundary, without adding
     * endpoint keys or serializers to the transport-independent Action model.
     *
     * This is suspendable transport IO, deliberately NOT the pure core State.
     * Applying Action.writer/onSuccess after success is the caller's policy.
     */
    @Suppress("FunctionName")
    suspend inline fun <State, reified S : Any, reified T : Any> Call(
        key: KClass<*>,
        state: State,
        reader: Reader<State, S>,
        pathParameters: Map<String, String> = emptyMap()
    ): Result<Contextual<T>> {
        val declared = requireNotNull(api[key]) { "Unknown API endpoint key: " + key }
        require(declared.requestType == S::class && declared.responseType == T::class) {
            "Endpoint " + key + " declares " + declared.requestType + " -> " +
                declared.responseType + ", not " + S::class + " -> " + T::class
        }
        val input = reader(state)
        return when (declared.method) {
            "GET" -> {
                require(input is Parameters) {
                    "GET " + key + " requires Api.Parameters input for query parameters"
                }
                request<T>(
                    key, pathParameters,
                    queryParameters = input.queryParams.groupBy({ it.first }, { it.second })
                )
            }
            "HEAD" -> error("Typed Result client has no HEAD response body: " + key)
            else -> send<S, T>(key, input, pathParameters)
        }
    }
}
