package org.evoleq.mathcat.routing

import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoutingTest {
    @Test fun nestedSolytonSyntaxAndSpeakingDeepLinksRoundTrip() {
        val definitions = routes {
            route("app") {
                layout("shell")
                requires("authenticated")
                route("organisationen/{organisation}/anteile") {
                    name("shares")
                    requires("shares.read")
                }
                route("bid/send/:cryptoId") { name("sendBid") }
            }
        }
        val compiled = assertIs<Sum2.Right<CompiledRoutes>>(CompileRoutes(definitions)).value
        val link = assertIs<Sum2.Right<String>>(compiled.url(
            "shares", mapOf("organisation" to "Sonnenhof Süd"),
            listOf("tab" to "history", "tag" to "a", "tag" to "b")
        )).value
        assertEquals("/app/organisationen/Sonnenhof%20S%C3%BCd/anteile?tab=history&tag=a&tag=b", link)
        val matched = assertIs<Sum2.Right<RouteMatch>>(compiled.match(link + "#section")).value
        assertEquals("shares", matched.name)
        assertEquals("Sonnenhof Süd", matched.parameters["organisation"])
        assertEquals(listOf("a", "b"), matched.query["tag"])
        assertEquals(setOf("authenticated", "shares.read"), matched.requires)
        assertEquals(listOf("shell"), matched.layouts)
        assertEquals("sendBid", assertIs<Sum2.Right<RouteMatch>>(compiled.match("/app/bid/send/a12/")).value.name)
    }

    @Test fun staticOutranksParameters() {
        val compiled = assertIs<Sum2.Right<CompiledRoutes>>(CompileRoutes(routes {
            route("app/:id") { name("item") }
            route("app/settings") { name("settings") }
        })).value
        assertEquals("settings", assertIs<Sum2.Right<RouteMatch>>(compiled.match("/app/settings")).value.name)
        assertEquals("item", assertIs<Sum2.Right<RouteMatch>>(compiled.match("/app/123")).value.name)
    }

    @Test fun competingVariableSiblingsAreRejected() {
        val result = CompileRoutes(routes {
            route("organisationen/:name") { name("byName") }
            route("organisationen/{id}") { name("byId") }
        })
        assertTrue(assertIs<Sum2.Left<List<RouteError>>>(result).value.any { it is RouteError.Ambiguous })
    }

    @Test fun duplicateNamesCannotBePublished() {
        val result = CompileRoutes(routes {
            route("a") { name("same") }
            route("b") { name("same") }
        })
        assertTrue(assertIs<Sum2.Left<List<RouteError>>>(result).value.any { it is RouteError.DuplicateName })
    }

    @Test fun pureFragmentsComposeWithAValidatedResult() {
        val accounts = routes { route("accounts/:account") { name("account") } }
        val applications = routes { route("apps/:app") { name("application") } }
        val compiled = assertIs<Sum2.Right<CompiledRoutes>>(CompileRoutes(routes {
            route("app") {
                include(accounts)
                include(applications)
            }
        })).value
        assertEquals("account", assertIs<Sum2.Right<RouteMatch>>(compiled.match("/app/accounts/42")).value.name)
    }

    @Test fun malformedEncodingAndMissingParametersAreValues() {
        val compiled = assertIs<Sum2.Right<CompiledRoutes>>(CompileRoutes(routes {
            route("orgs/:slug") { name("org") }
        })).value
        assertIs<Sum2.Left<RouteError>>(compiled.match("/orgs/%ZZ"))
        assertEquals(RouteError.MissingParameter("slug"),
            assertIs<Sum2.Left<RouteError>>(compiled.url("org")).value)
        assertIs<Sum2.Left<RouteError>>(compiled.match("https://example.com/orgs/a"))
    }
}
