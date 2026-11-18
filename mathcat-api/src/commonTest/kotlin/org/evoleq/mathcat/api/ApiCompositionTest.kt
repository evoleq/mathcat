package org.evoleq.mathcat.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiCompositionTest {
    data class Request(val value: Int)
    data class Reply(val value: Int)

    private val createAuction = Endpoint<Request, Reply>("create", HttpMethod.POST, "create")
    private val readUsers = Endpoint<Unit, String>("list", HttpMethod.GET, "/all/")

    @Test
    fun independentModuleFragmentsComposeWithOriginalEndpointProvenance() {
        val auctions = api("auctions") { group("admin") { endpoint(createAuction) } }
        val users = api("users") { endpoint(readUsers) }
        val app = api("app") {
            module("/api/v1/auction/", auctions)
            module("api/v1/users", users)
        }

        assertEquals(2, app.endpoints.size)
        val auctionRoute = app.requireEndpoint(createAuction)
        assertEquals("/api/v1/auction/create", auctionRoute.path)
        assertEquals(HttpMethod.POST, auctionRoute.method)
        assertEquals(EndpointOrigin("auctions", listOf("admin")), auctionRoute.origin)
        assertEquals("/api/v1/users/all", app.requireEndpoint(readUsers).path)
        assertEquals("users", app.requireEndpoint(readUsers).origin.moduleId)
    }

    @Test
    fun nestedModulesHaveAssociativePathCompositionAndPreserveGroups() {
        val leaf = api("leaf") { group("private") { endpoint(createAuction) } }
        val intermediate = api("intermediate") { module("orders", leaf) }
        val nested = api("app") { group("external") { module("v1", intermediate) } }
        val flattened = api("app") { group("external") { module("v1/orders", leaf) } }

        assertEquals("/v1/orders/create", nested.requireEndpoint(createAuction).path)
        assertEquals(flattened.endpoints, nested.endpoints)
        assertEquals(listOf("external", "private"), nested.requireEndpoint(createAuction).origin.groups)
        assertEquals("leaf", nested.requireEndpoint(createAuction).origin.moduleId)
    }

    @Test
    fun groupIsMetadataNotAnUrlPrefix() {
        val grouped = api("feature") {
            group("auditing") { group("operations") { endpoint(createAuction) } }
        }
        assertEquals("/create", grouped.requireEndpoint(createAuction).path)
        assertEquals(listOf("auditing", "operations"), grouped.requireEndpoint(createAuction).origin.groups)
    }

    @Test
    fun canonicalPathsCollapseSlashesAndRejectInvalidSegments() {
        val details = Endpoint<Unit, Reply>("details", HttpMethod.GET, "//{id}//detail/")
        val mounted = api("feature") { module("/api//v1/", api("detail") { endpoint(details) }) }
        assertEquals("/api/v1/{id}/detail", mounted.requireEndpoint(details).path)
        assertFailsWith<IllegalArgumentException> { Endpoint<Unit, Reply>("wrong", HttpMethod.GET, "../bad") }
        assertFailsWith<IllegalArgumentException> { Endpoint<Unit, Reply>("wrong", HttpMethod.GET, "/bad?x=1") }
        assertFailsWith<IllegalArgumentException> { api("feature") { module("../bad", mounted) } }
    }

    @Test
    fun sameMethodAndCanonicalPathCannotBeDeclaredTwice() {
        val other = Endpoint<Request, Reply>("other", HttpMethod.POST, "//create/")
        val ex = assertFailsWith<IllegalArgumentException> {
            api("feature") { endpoint(createAuction); endpoint(other) }
        }
        kotlin.test.assertTrue(ex.message.orEmpty().contains("Duplicate route"))
    }

    @Test
    fun duplicateOriginalEndpointIdentitiesAreRejectedEvenAcrossIndependentFragments() {
        val alsoCreate = Endpoint<Request, Reply>("create", HttpMethod.PUT, "different")
        val first = api("auctions") { endpoint(createAuction) }
        val second = api("auctions") { endpoint(alsoCreate) }
        assertFailsWith<IllegalArgumentException> {
            api("application") { module("first", first); module("second", second) }
        }
        assertFailsWith<IllegalArgumentException> {
            api("application") { module("first", first); module("second", first) }
        }
    }

    @Test
    fun methodsAndOriginalModulesProvideDistinctEndpointIdentities() {
        val delete = Endpoint<Request, Reply>("remove", HttpMethod.DELETE, "resource")
        val get = Endpoint<Unit, Reply>("list", HttpMethod.GET, "resource")
        val first = api("first") { endpoint(get) }
        val second = api("second") { endpoint(delete) }
        val app = api("root") { module("/", first); module("/", second) }
        assertEquals(2, app.endpoints.size)
        assertEquals("/resource", app.requireEndpoint(get).path)
        assertEquals("/resource", app.requireEndpoint(delete).path)
    }

    @Test
    fun missingOrForgedSameNamedDeclarationCannotBeResolvedByUncheckedCast() {
        val app = api("feature") { endpoint(createAuction) }
        val forged = Endpoint<Unit, String>("create", HttpMethod.POST, "create")
        assertNull(app.resolve(forged))
        assertNotNull(app.resolve(createAuction))
        assertFailsWith<IllegalArgumentException> { app.requireEndpoint(forged) }
    }

    @Test
    fun builtFragmentsAreSnapshotsIndependentOfLaterCompositions() {
        val a = api("a") { endpoint(createAuction) }
        val first = api("root") { module("one", a) }
        val second = api("root") { module("two", a) }
        assertEquals("/create", a.requireEndpoint(createAuction).path)
        assertEquals("/one/create", first.requireEndpoint(createAuction).path)
        assertEquals("/two/create", second.requireEndpoint(createAuction).path)
    }
}
