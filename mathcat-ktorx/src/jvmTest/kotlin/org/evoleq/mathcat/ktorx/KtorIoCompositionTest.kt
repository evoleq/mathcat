package org.evoleq.mathcat.ktorx

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams
import org.evoleq.mathcat.result.ExceptionTransforms
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.result.map
import org.evoleq.mathcat.serialization.ResultSerializer
import org.evoleq.mathcat.serialization.Serializers
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorIoCompositionTest {
    private class Greeting
    private class ReadName : EmptyParams()
    private class MissingUser : IllegalArgumentException("Unknown user")

    private data class Environment(val label: String)
    private data class Base(val call: ApplicationCall, val environment: Environment)
    private val userId = UUID.fromString("ee9a1d7e-973f-42ef-a272-3765b00b61ba")
    private val codecs = Serializers { add<String>(String.serializer()) }

    private val unwrap: KlAction<Base, Result<Contextual<String>>, Result<String>> =
        { result -> Action { base -> (result map { it.data }) to base } }

    @Test
    fun receiveContextualTransformRespondAndFreshBasePerCall() = testApplication {
        val routeApi = Api { post<String, String>(Greeting::class, "greeting") }
        val observed = mutableListOf<Contextual<String>>()
        val bases = mutableListOf<Base>()
        val environment = Environment("greeter")
        val io = KtorIO<Base>(
            callOf = { it.call }, serializers = codecs,
            principal = { Result.Success(userId) }
        )
        val track: KlAction<Base, Result<Contextual<String>>, Result<Contextual<String>>> =
            { result ->
                Action { base ->
                    if (result is Result.Success) observed += result.data
                    result to base
                }
            }

        application {
            routing {
                bindEndpoint(routeApi, Greeting::class, environment, ::Base) { base ->
                    bases += base
                    with(io) {
                        (
                            ReceiveContextual<String>() *
                            TransformContextual<String, String> { it.uppercase() } *
                            track * unwrap * Respond<String>()
                        ) runOn base
                    }
                }
            }
        }

        val first = client.post("/greeting") {
            contentType(ContentType.Application.Json)
            header(CONTEXT_HEADER, "workspace-one")
            setBody("\"hello\"")
        }
        val second = client.post("/greeting") {
            contentType(ContentType.Application.Json)
            header(CONTEXT_HEADER, "workspace-two")
            setBody("\"bye\"")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(Result.Success("HELLO"),
            Json.decodeFromString(ResultSerializer(String.serializer()), first.bodyAsText()))
        assertEquals(Result.Success("BYE"),
            Json.decodeFromString(ResultSerializer(String.serializer()), second.bodyAsText()))
        assertEquals(listOf("workspace-one", "workspace-two"), observed.map { it.context })
        assertEquals(listOf("HELLO", "BYE"), observed.map { it.data })
        assertTrue(observed.all { it.userId == userId })
        assertEquals(2, bases.size)
        assertTrue(bases[0] !== bases[1])
        assertTrue(bases[0].call !== bases[1].call)
    }

    @Test
    fun featureExceptionPoliciesAreComposedAndRespondUsesTheirStatus() = testApplication {
        val api = Api { post<String, String>(Greeting::class, "greeting") }
        val userErrors = ExceptionTransforms { on<MissingUser>(404) }
        val domainErrors = ExceptionTransforms { include(userErrors) }
        val io = KtorIO<Base>(callOf = { it.call }, serializers = codecs, transforms = domainErrors)
        val environment = Environment("errors")
        application {
            routing {
                bindEndpoint(api, Greeting::class, environment, ::Base) { base ->
                    with(io) {
                        (Receive<String>() *
                            Transform<String, String> { throw MissingUser() } *
                            Respond<String>()) runOn base
                    }
                }
            }
        }
        val response = client.post("/greeting") {
            contentType(ContentType.Application.Json)
            setBody("\"hello\"")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(Result.Failure.Message("Unknown user"),
            Json.decodeFromString(ResultSerializer(String.serializer()), response.bodyAsText()))
    }

    @Test
    fun missingContextIsAnExplicitMappableFailure() = testApplication {
        val api = Api { post<String, String>(Greeting::class, "greeting") }
        val policy = ExceptionTransforms { on<MissingContextHeader>(400) }
        val io = KtorIO<Base>(
            callOf = { it.call }, serializers = codecs, transforms = policy,
            principal = { Result.Success(userId) }
        )
        val environment = Environment("context")
        application {
            routing {
                bindEndpoint(api, Greeting::class, environment, ::Base) { base ->
                    with(io) {
                        (ReceiveContextual<String>() * unwrap * Respond<String>()) runOn base
                    }
                }
            }
        }
        val response = client.post("/greeting") {
            contentType(ContentType.Application.Json)
            setBody("\"hello\"")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(Result.Failure.Message("Missing mandatory header: Context"),
            Json.decodeFromString(ResultSerializer(String.serializer()), response.bodyAsText()))
    }

    @Test
    fun queryParameterOverloadAndExplicitExceptionTransformAreCompatible() = testApplication {
        val api = Api { get<ReadName, String>(ReadName::class, "greeting") }
        val io = KtorIO<Base>(
            callOf = { it.call }, serializers = codecs,
            principal = { Result.Success(userId) }
        )
        val environment = Environment("query")
        val transform: Result.Failure.Exception.() ->
            Pair<HttpStatusCode, Result.Failure.Message> = {
                HttpStatusCode.Conflict to Result.Failure.Message("app-level error")
            }
        application {
            routing {
                bindEndpoint(api, ReadName::class, environment, ::Base) { base ->
                    with(io) {
                        (
                            ReceiveContextual { params -> params["name"] ?: "anonymous" } *
                            unwrap *
                            Respond<String> { transform() }
                        ) runOn base
                    }
                }
            }
        }
        val response = client.get("/greeting?name=Alice") {
            header(CONTEXT_HEADER, "workspace")
        }
        assertEquals(Result.Success("Alice"),
            Json.decodeFromString(ResultSerializer(String.serializer()), response.bodyAsText()))
    }
}
