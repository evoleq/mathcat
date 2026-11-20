package evoleq.toolkit.ksp.fixture

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import evoleq.toolkit.actions.Action
import evoleq.toolkit.api.Api
import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.storage.Storage
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.ktorx.ApiClient
import evoleq.toolkit.ktorx.ApiClientResolver
import evoleq.toolkit.ktorx.context.data.Contextual
import evoleq.toolkit.middleware.Call
import evoleq.toolkit.middleware.MiddlewareRuntime
import evoleq.toolkit.middleware.at
import evoleq.toolkit.result.Result
import evoleq.toolkit.serialization.ResultSerializer
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
