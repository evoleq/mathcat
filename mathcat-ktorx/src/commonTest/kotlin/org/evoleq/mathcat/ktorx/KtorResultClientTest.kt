package org.evoleq.mathcat.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.ResultSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorResultClientTest {
    private class ReadUser : EmptyParams()
    private class User

    @Test
    fun returnsResultWithContextFromResponseHeader() = runTest {
        val api = Api { get<ReadUser, String>(ReadUser::class, "users/{userId}") }
        val serialized = Json.encodeToString(
            ResultSerializer(String.serializer()), Result.Success("Alice")
        )
        val client = HttpClient(MockEngine { req ->
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("/users/42", req.url.encodedPath)
            respond(serialized, headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                CONTEXT_HEADER to listOf("workspace-7")
            ))
        })
        try {
            val result = client.requestResultEndpoint(api, ReadUser::class,
                "https://example.test", String.serializer(), mapOf("userId" to "42"))
            assertEquals(Result.Success(Contextual("workspace-7", "Alice")), result)
        } finally {
            client.close()
        }
    }

    @Test
    fun preservesHttpStatusWhenTheServerRespondsWithResultFailure() = runTest {
        val api = Api { get<ReadUser, String>(ReadUser::class, "users/{userId}") }
        val payload = Json.encodeToString(
            ResultSerializer(String.serializer()),
            Result.Failure.Message("access denied") as Result<String>
        )
        val client = HttpClient(MockEngine {
            respond(payload, status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertEquals(Result.Failure.HttpStatusMessage(403, "access denied"),
                client.requestResultEndpoint(api, ReadUser::class,
                    "https://example.test", String.serializer(), mapOf("userId" to "42")))
        } finally {
            client.close()
        }
    }

    @Test
    fun nonJsonHttpErrorStillIncludesStatusAndMessage() = runTest {
        val api = Api { get<ReadUser, String>(ReadUser::class, "users/{userId}") }
        val client = HttpClient(MockEngine {
            respond("Not Found", status = HttpStatusCode.NotFound)
        })
        try {
            assertEquals(Result.Failure.HttpStatusMessage(404, "Not Found"),
                client.requestResultEndpoint(api, ReadUser::class,
                    "https://example.test", String.serializer(), mapOf("userId" to "42")))
        } finally {
            client.close()
        }
    }
}
