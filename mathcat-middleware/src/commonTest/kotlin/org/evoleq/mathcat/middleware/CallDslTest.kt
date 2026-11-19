package org.evoleq.mathcat.middleware

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Storage
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.ktorx.ApiClient
import org.evoleq.mathcat.ktorx.ApiClientResolver
import org.evoleq.mathcat.ktorx.CONTEXT_HEADER
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.ResultSerializer
import org.evoleq.mathcat.serialization.Serializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallDslTest {
    private class LoginKey
    private class MissingKey
    private data class App(val input: String, val loggedIn: Boolean = false)
    private val codecs = Serializers { add<String>(String.serializer()) }

    private fun response(message: String) =
        Json.encodeToString(ResultSerializer(String.serializer()), Result.Success(message))

    private fun action() = Action(
        name = "login",
        reader = Reader<App, String> { it.input },
        writer = Writer<App, String> { message -> { original -> original.copy(input = message) } }
    ).at(LoginKey::class)

    @Test
    fun standaloneCallReadsEndpointFromBoundActionWithoutGatewayOrCodecs() = runTest {
        val api = Api { post<String, String>(LoginKey::class, "login") }
        val client = HttpClient(MockEngine { req ->
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("/login", req.url.encodedPath)
            respond(response("welcome"), headers = headersOf(CONTEXT_HEADER, "tenant-1"))
        })
        try {
            var state = App("ada")
            var reads = 0
            val storage = Storage(
                read = { ++reads; state },
                write = { state = it }
            )
            val runtime = MiddlewareRuntime(storage, ApiClientResolver<App> {
                ApiClient(client, api, "https://example.test", codecs)
            })
            val result = Call(action()) runOn runtime
            assertEquals(Result.Success(Contextual("tenant-1", "welcome")), result)
            assertEquals(1, reads)
            // The middleware does not automatically apply the Action.writer.
            assertEquals(App("ada"), state)
        } finally {
            client.close()
        }
    }

    @Test
    fun callResolvesCurrentClientFromStorageAtEveryRun() = runTest {
        val api = Api { post<String, String>(LoginKey::class, "login") }
        val guest = HttpClient(MockEngine { respond(response("guest")) })
        val member = HttpClient(MockEngine { respond(response("member")) })
        try {
            var current = App("ada")
            val storage = Storage(read = { current }, write = { current = it })
            val runtime = MiddlewareRuntime(storage, ApiClientResolver<App> { state ->
                ApiClient(
                    if (state.loggedIn) member else guest,
                    api,
                    "https://example.test",
                    codecs
                )
            })
            val effect = Call(action())
            assertEquals(Result.Success(Contextual("", "guest")), effect runOn runtime)
            storage.write(App("ada", loggedIn = true))
            assertEquals(Result.Success(Contextual("", "member")), effect runOn runtime)
        } finally {
            guest.close()
            member.close()
        }
    }

    @Test
    fun invalidBoundKeyFailsBeforeNetwork() = runTest {
        val api = Api { post<String, String>(LoginKey::class, "login") }
        val client = HttpClient(MockEngine { error("No HTTP request expected") })
        try {
            val storage = Storage(read = { App("ada") }, write = { _: App -> })
            val runtime = MiddlewareRuntime(storage, ApiClientResolver<App> {
                ApiClient(client, api, "https://example.test", codecs)
            })
            val mismatched = action().copy(endPoint = MissingKey::class)
            assertFailsWith<IllegalArgumentException> {
                Call(mismatched) runOn runtime
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun callEffectSupportsPurePostProcessingWithoutStateMutation() = runTest {
        val api = Api { post<String, String>(LoginKey::class, "login") }
        val client = HttpClient(MockEngine { respond(response("done")) })
        try {
            val runtime = MiddlewareRuntime(
                Storage(read = { App("ada") }, write = { _: App -> }),
                ApiClientResolver<App> { ApiClient(client, api, "https://example.test", codecs) }
            )
            val transformed = Call(action()).map { it.toString().contains("done") }
            assertEquals(true, transformed runOn runtime)
        } finally {
            client.close()
        }
    }
}
