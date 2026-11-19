package org.evoleq.mathcat.ktorx

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.Parameters
import org.evoleq.mathcat.api.QueryParams
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.ResultSerializer
import org.evoleq.mathcat.serialization.Serializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiClientDxTest {
    private class Greet
    private class Search
    private data class SearchParams(override val queryParams: QueryParams) : Parameters()
    private data class State(val input: String, val loggedIn: Boolean = false)

    private val strings = Serializers { add<String>(String.serializer()) }
    private fun response(text: String) = Json.encodeToString(
        ResultSerializer(String.serializer()), Result.Success(text)
    )

    @Test
    fun clientUsesRegistryOnceRatherThanSerializersAtCallSites() = runTest {
        val api = Api { post<String, String>(Greet::class, "v1/greet") }
        val http = HttpClient(MockEngine { req ->
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("/v1/greet", req.url.encodedPath)
            respond(response("Hi, Ada"), headers = headersOf(CONTEXT_HEADER, "tenant-a"))
        })
        try {
            val boundary = ApiClient(http, api, "https://api.test", strings)
            val actionReader: Reader<State, String> = Reader { it.input }
            val result = boundary.executeAction<State, String, String>(
                Greet::class, State("Ada"), actionReader
            )
            assertEquals(Result.Success(Contextual("tenant-a", "Hi, Ada")), result)
        } finally {
            http.close()
        }
    }

    @Test
    fun getReaderContributesQueryParametersWithoutPayloadSerializer() = runTest {
        val api = Api {
            get<SearchParams, String>(Search::class, "lookup/{id}")
        }
        val http = HttpClient(MockEngine { req ->
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("/lookup/a%20b", req.url.encodedPath)
            assertEquals("Ada", req.url.parameters["name"])
            respond(response("found"))
        })
        try {
            val boundary = ApiClient(http, api, "https://api.test", strings)
            val reader: Reader<State, SearchParams> = Reader {
                SearchParams(listOf("name" to it.input))
            }
            val result = boundary.executeAction<State, SearchParams, String>(
                Search::class, State("Ada"), reader, mapOf("id" to "a b")
            )
            assertEquals(Result.Success(Contextual("", "found")), result)
        } finally {
            http.close()
        }
    }

    @Test
    fun errorsOnMissingRegistryEntryOrMismatchingApiTypesBeforeNetwork() = runTest {
        val api = Api { post<String, String>(Greet::class, "greet") }
        val http = HttpClient(MockEngine { error("Must not issue a network request") })
        try {
            val missing = ApiClient(http, api, "https://api.test", Serializers {})
            assertFailsWith<IllegalArgumentException> {
                missing.send<String, String>(Greet::class, "Ada")
            }
            val ready = ApiClient(http, api, "https://api.test", strings)
            assertFailsWith<IllegalArgumentException> {
                ready.send<String, Int>(Greet::class, "Ada")
            }
        } finally {
            http.close()
        }
    }

}
