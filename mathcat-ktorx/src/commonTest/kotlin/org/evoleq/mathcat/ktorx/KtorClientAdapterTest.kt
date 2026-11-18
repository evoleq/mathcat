package org.evoleq.mathcat.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.builtins.serializer
import runTest
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorClientAdapterTest {
    private class ReadUser : EmptyParams()
    private class User
    private class CreateUser

    @Test
    fun endpointTemplatesSubstituteExactlyTheDeclaredNames() {
        assertEquals("users/42/roles/admin",
            resolvePathParameters("users/{userId}/roles/{roleId}",
                mapOf("userId" to "42", "roleId" to "admin")))
        assertEquals("users/hello%20world",
            resolvePathParameters("users/{userId}", mapOf("userId" to "hello world")))
        assertFailsWith<IllegalArgumentException> {
            resolvePathParameters("users/{userId}", emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            resolvePathParameters("users/{userId}", mapOf("userId" to "1", "other" to "extra"))
        }
    }

    @Test
    fun clientUsesTheMethodAndPathFromTheOriginalApiMap() = runTest {
        val api = Api {
            module("api") {
                get<ReadUser, User>(ReadUser::class, "users/{id}")
            }
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.GET, request.method)
            assertEquals("/api/users/42", request.url.encodedPath)
            respond(
                content = "\"found\"",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        try {
            val result = client.requestEndpoint(
                api = api,
                key = ReadUser::class,
                baseUrl = "https://example.test",
                responseSerializer = String.serializer(),
                pathParameters = mapOf("id" to "42"),
                queryParameters = mapOf("expand" to listOf("roles"))
            )
            assertEquals("found", result)
        } finally {
            client.close()
        }
    }

    @Test
    fun clientCanSerializeRequestBodiesWithoutKsp() = runTest {
        val api = Api { post<String, String>(CreateUser::class, "users/create") }
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("/users/create", request.url.encodedPath)
            respond("\"saved\"", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertEquals("saved",
                client.sendEndpoint(
                    api, CreateUser::class, "https://example.test",
                    payload = "hello", requestSerializer = String.serializer(),
                    responseSerializer = String.serializer()
                )
            )
        } finally {
            client.close()
        }
    }
}
