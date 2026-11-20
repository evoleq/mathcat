package evoleq.toolkit.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import evoleq.toolkit.api.Api
import kotlin.reflect.KClass

/**
 * Run an HTTP call described by the original Api map.
 *
 * The caller supplies the response codec explicitly. That codec may come
 * from a manual or KSP-generated Serializers registry; KSP is never required.
 * This first adapter returns decoded T, not yet the extracted Result<T>.
 */
suspend fun <T : Any> HttpClient.requestEndpoint(
    api: Api,
    key: KClass<*>,
    baseUrl: String,
    responseSerializer: KSerializer<T>,
    pathParameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, List<String>> = emptyMap(),
    json: Json = Json,
    configure: HttpRequestBuilder.() -> Unit = {}
): T {
    val endpoint = requireNotNull(api[key]) { "Unknown endpoint key: " + key }
    val path = resolvePathParameters(endpoint.url, pathParameters)
    val fullUrl = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    val response = request(fullUrl) {
        method = HttpMethod.parse(endpoint.method)
        url {
            for ((name, values) in queryParameters) {
                for (value in values) parameters.append(name, value)
            }
        }
        configure()
    }
    check(response.status.value in 200..299) {
        "HTTP " + response.status.value + " calling " + endpoint.method + " " + path
    }
    return json.decodeFromString(responseSerializer, response.bodyAsText())
}

/** Request-body variant of the same declared endpoint. */
suspend fun <S : Any, T : Any> HttpClient.sendEndpoint(
    api: Api,
    key: KClass<*>,
    baseUrl: String,
    payload: S,
    requestSerializer: KSerializer<S>,
    responseSerializer: KSerializer<T>,
    pathParameters: Map<String, String> = emptyMap(),
    json: Json = Json
): T = requestEndpoint(
    api = api,
    key = key,
    baseUrl = baseUrl,
    responseSerializer = responseSerializer,
    pathParameters = pathParameters,
    json = json
) {
    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    setBody(json.encodeToString(requestSerializer, payload))
}
