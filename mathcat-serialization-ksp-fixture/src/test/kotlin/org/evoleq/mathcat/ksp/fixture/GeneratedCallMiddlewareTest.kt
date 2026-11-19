package org.evoleq.mathcat.ksp.fixture

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Storage
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.ktorx.ApiClient
import org.evoleq.mathcat.ktorx.ApiClientResolver
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.middleware.Call
import org.evoleq.mathcat.middleware.MiddlewareRuntime
import org.evoleq.mathcat.middleware.at
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.ResultSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedCallMiddlewareTest {
    private class LoginKey
    private data class App(val login: Login)

    @Test
    fun standaloneCallSelectsKeyFromActionAndCodecsFromGeneratedRegistry() = runBlocking {
        val api = Api { post<Login, LoggedIn>(LoginKey::class, "login") }
        val http = HttpClient(MockEngine {
            respond(Json.encodeToString(
                ResultSerializer(LoggedIn.serializer()),
                Result.Success(LoggedIn("token"))
            ))
        })
        try {
            val runtime = MiddlewareRuntime(
                storage = Storage(read = { App(Login("Ada", "secret")) }, write = { _: App -> }),
                client = ApiClientResolver<App> {
                    ApiClient(http, api, "https://example.test", GeneratedFeatureSerializers)
                }
            )
            val action = Action(
                name = "login",
                reader = Reader<App, Login> { it.login },
                writer = Writer<App, LoggedIn> { _ -> { original -> original } }
            ).at(LoginKey::class)
            val result = Call(action) runOn runtime
            assertEquals(Result.Success(Contextual("", LoggedIn("token"))), result)
        } finally {
            http.close()
        }
    }
}
