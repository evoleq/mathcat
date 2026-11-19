package org.evoleq.mathcat.ktorx

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorServerAdapterTest {
    private class ReadUser : EmptyParams()
    private class User

    private data class Environment(val prefix: String)
    private data class Base(val call: ApplicationCall, val environment: Environment)

    @Test
    fun endpointBindingRegistersDeclaredRouteAndFreshContextPerRequest() = testApplication {
        val api = Api {
            module("api") {
                get<ReadUser, User>(ReadUser::class, "users/{userId}")
            }
        }
        val environment = Environment("user:")
        val calls = mutableListOf<ApplicationCall>()
        val bases = mutableListOf<Base>()

        application {
            routing {
                bindEndpoint(
                    api = api,
                    key = ReadUser::class,
                    environment = environment,
                    createBase = ::Base
                ) { base ->
                    bases += base
                    calls += base.call
                    base.call.respondText(base.environment.prefix +
                        base.call.parameters["userId"])
                }
            }
        }

        val first = client.get("/api/users/one")
        val second = client.get("/api/users/two")
        assertEquals("user:one", first.bodyAsText())
        assertEquals("user:two", second.bodyAsText())
        assertEquals(2, bases.size)
        assertTrue(bases[0] !== bases[1])
        assertTrue(calls[0] !== calls[1])
        assertTrue(bases.all { it.environment === environment })
    }
}
