package evoleq.toolkit.ktorx

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.client.request.header
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import evoleq.toolkit.api.Api
import evoleq.toolkit.result.Result
import evoleq.toolkit.serialization.ResultSerializer
import evoleq.toolkit.serialization.Serializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Executable example of installApi and request-local environments. */
class ApiRoutesDxTest {
    private class Greet
    private data class Environment(val prefix: String)
    private val serializers = Serializers { add<String>(String.serializer()) }

    @Test
    fun installationElidesCodecsAndEachRequestGetsItsOwnContext() = testApplication {
        val api = Api { module("api") { post<String, String>(Greet::class, "greet") } }
        val env = Environment("Hello, ")
        val observed = mutableListOf<RequestContext<Environment>>()

        application {
            routing {
                installApi(api, env, serializers) {
                    handle(Greet::class) { base ->
                        observed += base
                        (Receive<String>() *
                            Transform<String, String> { base.environment.prefix + it } *
                            Respond<String>()) runOn base
                    }
                }
            }
        }
        val first = client.post("/api/greet") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("\"Ada\"")
        }
        val second = client.post("/api/greet") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("\"Bob\"")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(Result.Success("Hello, Ada"),
            Json.decodeFromString(ResultSerializer(String.serializer()), first.bodyAsText()))
        assertEquals(Result.Success("Hello, Bob"),
            Json.decodeFromString(ResultSerializer(String.serializer()), second.bodyAsText()))
        assertEquals(2, observed.size)
        assertNotSame(observed[0], observed[1])
        assertNotSame(observed[0].call, observed[1].call)
        assertTrue(observed.all { it.environment === env })
    }
}
