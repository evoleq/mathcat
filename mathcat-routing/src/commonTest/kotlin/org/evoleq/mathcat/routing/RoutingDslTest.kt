package org.evoleq.mathcat.routing

import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Solyton DSL as executable documentation, including speaking deep links. */
class RoutingDslTest {
    private val routes = routing<String> {
        route("app") {
            wrap {
                access { true }
                layout { page -> "shell($page)" }
                route("organisationen/:organisation") {
                    route("anteile") {
                        name("shares")
                        component("shares-view")
                    }
                    route("verwaltung/{bereich}") {
                        name("management")
                        component("management-view")
                    }
                }
            }
        }
        route("login") {
            name("login")
            component("login-view")
        }
    }
    private val router = assertIs<Sum2.Right<CompiledRoutes<String>>>(routes.compile()).value

    @Test
    fun deepLinkInBrowserAddressBarWithSolytonLayoutAndParameters() {
        val hit = assertIs<Sum2.Right<RouteMatch<String>>>(
            router.match("/app/organisationen/sonnenhof/anteile")
        ).value
        assertEquals("shares", hit.name)
        assertEquals("sonnenhof", hit.parameter("organisation"))
        assertEquals("shell(shares-view)", hit.component)
        assertTrue(hit.isGranted())
    }

    @Test
    fun speakingUrlsRoundtripThroughNameAndEncodedParameters() {
        val url = assertIs<Sum2.Right<String>>(router.urlFor(
            "shares", mapOf("organisation" to "Sonnenhof & Freunde")
        )).value
        assertEquals("/app/organisationen/Sonnenhof%20%26%20Freunde/anteile", url)
        val matched = assertIs<Sum2.Right<RouteMatch<String>>>(router.match(url)).value
        assertEquals("Sonnenhof & Freunde", matched.parameter("organisation"))
        assertEquals(url, matched.location.toUrl())
    }

    @Test
    fun nestedVariablesAndRepeatedQueriesArePreserved() {
        val url = "/app/organisationen/sonnenhof/verwaltung/finanzen?tag=a&tag=b&name=M%C3%BCller+%26+Co#details"
        val result = assertIs<Sum2.Right<RouteMatch<String>>>(router.match(url)).value
        assertEquals("sonnenhof", result.parameter("organisation"))
        assertEquals("finanzen", result.parameter("bereich"))
        assertEquals(listOf("a", "b"), result.location.query["tag"])
        assertEquals("Müller & Co", result.queryParameter("name"))
        assertEquals("details", result.location.fragment)
        assertEquals(
            "/app/organisationen/sonnenhof/verwaltung/finanzen?tag=a&tag=b&name=M%C3%BCller%20%26%20Co#details",
            result.location.toUrl()
        )
    }

    @Test
    fun staticRouteOutranksVariableRouteRegardlessOfDeclarationOrder() {
        val r = routing<String> {
            route("app/organisationen/:slug") { name("dynamic"); component("dynamic") }
            route("app/organisationen/new") { name("new"); component("static") }
        }
        val compiled = assertIs<Sum2.Right<CompiledRoutes<String>>>(r.compile()).value
        assertEquals(
            "static",
            assertIs<Sum2.Right<RouteMatch<String>>>(
                compiled.match("/app/organisationen/new")
            ).value.component
        )
    }

    @Test
    fun ambiguousRoutesAndDuplicateNamesOrVariablesFailAtCompileTime() {
        val ambiguous = routing<String> {
            route("org/:id") { component("one") }
            route("org/:name") { component("two") }
        }
        assertIs<RouteFailure.AmbiguousPattern>(
            assertIs<Sum2.Left<RouteFailure>>(ambiguous.compile()).value
        )
        val overlapping = routing<String> {
            route("app/:which/new") { component("one") }
            route("app/static/:which") { component("two") }
        }
        assertIs<RouteFailure.AmbiguousPattern>(
            assertIs<Sum2.Left<RouteFailure>>(overlapping.compile()).value
        )
        val duplicate = routing<String> {
            route("one") { name("same"); component("one") }
            route("two") { name("same"); component("two") }
        }
        assertEquals(RouteFailure.DuplicateName("same"),
            assertIs<Sum2.Left<RouteFailure>>(duplicate.compile()).value)
        val repeated = routing<String> {
            route("org/:name/shares/:name") { component("bad") }
        }
        assertEquals(RouteFailure.DuplicateParameter("name"),
            assertIs<Sum2.Left<RouteFailure>>(repeated.compile()).value)
    }

    @Test
    fun accessIsCheckedOnResolutionNotOnBuildOrMatching() {
        var calls = 0
        val r = routing<String> {
            wrap {
                access { calls++; false }
                route("private/:member") { name("private"); component("page") }
            }
        }
        assertEquals(0, calls)
        val compiled = assertIs<Sum2.Right<CompiledRoutes<String>>>(r.compile()).value
        assertEquals(0, calls)
        assertIs<Sum2.Right<RouteMatch<String>>>(compiled.match("/private/alice"))
        assertEquals(0, calls)
        assertEquals(RouteFailure.AccessDenied("private"),
            assertIs<Sum2.Left<RouteFailure>>(compiled.resolve("/private/alice")).value)
        assertEquals(1, calls)
    }

    @Test
    fun nonProdRouteAndChildrenOnlyLayoutPreserveOldDslIntent() {
        val r = routing<String> {
            route("app") {
                component("root")
                layout { "layout($it)" }
                nonProdRoute("debug", "dev") { component("test") }
                nonProdRoute("secret", "prod") { component("never") }
            }
        }
        val compiled = assertIs<Sum2.Right<CompiledRoutes<String>>>(r.compile()).value
        assertEquals("root", assertIs<Sum2.Right<RouteMatch<String>>>(
            compiled.match("/app")).value.component)
        assertEquals("layout(test)", assertIs<Sum2.Right<RouteMatch<String>>>(
            compiled.match("/app/debug")).value.component)
        assertIs<Sum2.Left<RouteFailure>>(compiled.match("/app/secret"))
    }

    @Test
    fun fragmentsComposeWithProductAndEmptyIdentity() {
        val a = routing<String> { route("a") { component("A") } }
        val b = routing<String> { route("b") { component("B") } }
        assertEquals((a * b).definitions, (EmptyRoutes<String>() * a * b).definitions)
        assertEquals(((a * b) * a).definitions, (a * (b * a)).definitions)
        val combined = assertIs<Sum2.Right<CompiledRoutes<String>>>((a * b).compile()).value
        assertIs<Sum2.Right<RouteMatch<String>>>(combined.match("/a"))
        assertIs<Sum2.Right<RouteMatch<String>>>(combined.match("/b"))
    }

    @Test
    fun malformedInternalUrlsAndInvalidReverseParametersAreExplicit() {
        assertIs<RouteFailure.MalformedUrl>(
            assertIs<Sum2.Left<RouteFailure>>(router.match("//evil.com")).value
        )
        assertIs<RouteFailure.MalformedUrl>(
            assertIs<Sum2.Left<RouteFailure>>(router.match("/app/organisationen/%ZZ/anteile")).value
        )
        assertEquals(RouteFailure.MissingParameter("organisation"),
            assertIs<Sum2.Left<RouteFailure>>(router.urlFor("shares")).value)
        assertEquals(RouteFailure.UnexpectedParameter("unknown"),
            assertIs<Sum2.Left<RouteFailure>>(router.urlFor(
                "shares", mapOf("organisation" to "hof", "unknown" to "v")
            )).value)
    }
}
