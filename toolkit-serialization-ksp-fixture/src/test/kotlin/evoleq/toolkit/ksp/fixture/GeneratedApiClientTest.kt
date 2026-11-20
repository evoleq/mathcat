package evoleq.toolkit.ksp.fixture

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import evoleq.toolkit.api.Api
import evoleq.toolkit.ktorx.ApiClient
import evoleq.toolkit.ktorx.context.data.Contextual
import evoleq.toolkit.result.Result
import evoleq.toolkit.serialization.ResultSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedApiClientTest {
    private class SignIn

    @Test
    fun generatedRegistryElidesBothRequestAndResponseSerializers() = runBlocking {
        val api = Api { post<Login, LoggedIn>(SignIn::class, "login") }
        val http = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            respond(Json.encodeToString(
                ResultSerializer(LoggedIn.serializer()),
                Result.Success(LoggedIn("token"))
            ))
        })
        try {
            // KSP generated this registry from two different source files.
            // The actual client call mentions no serializer whatsoever.
            val boundary = ApiClient(http, api, "https://example.test", GeneratedFeatureSerializers)
            val result = boundary.send<Login, LoggedIn>(
                SignIn::class, Login("Ada", "secret")
            )
            assertEquals(Result.Success(Contextual("", LoggedIn("token"))), result)
        } finally {
            http.close()
        }
    }
}
