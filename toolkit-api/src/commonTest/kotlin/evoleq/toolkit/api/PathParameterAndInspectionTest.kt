package evoleq.toolkit.api

import evoleq.toolkit.core.reader.Reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathParameterAndInspectionTest {
    class ReadUser
    class User
    class ReadRole
    class Login
    class LoggedIn
    class OtherResponse
    class Query : EmptyParams()
    class PathLookup

    @Test
    fun kTorStyleNamedPathParametersSurviveModularComposition() {
        val users = Api {
            get<Query, User>(
                key = ReadUser::class,
                url = "/{userId}",
                parameters = setOf("expand")
            )
        }
        val app = Api { module("/api/v1/users/", users) }
        val endpoint = app[ReadUser::class] ?: error("Not mounted")
        assertEquals("/api/v1/users/{userId}", endpoint.url)
        assertEquals(setOf("expand"), endpoint.parameters)
        assertTrue(app.validateRoutes().isEmpty())
        // A mounted fragment is copied, not rewritten in place.
        assertEquals("/{userId}", users[ReadUser::class]?.url)
    }

    @Test
    fun nestedModuleRoutesKeepAllPlaceholders() {
        val leaf = Api {
            get<Query, User>(
                key = ReadRole::class,
                url = "{roleId}"
            )
        }
        val section = Api { module("users/{userId}/roles/", leaf) }
        val app = Api { module("api/v1/", section) }
        assertEquals("api/v1/users/{userId}/roles/{roleId}", app[ReadRole::class]?.url)
        assertTrue(app.validateRoutes().isEmpty())
    }

    @Test
    fun differentParamNamesHaveSameRouteShapeForCollisionDiagnostics() {
        val api = Api {
            get<Query, User>(ReadUser::class, "users/{id}")
            get<Query, User>(ReadRole::class, "/users/{userId}/")
        }
        assertEquals(1, api.validateRoutes().count { it.kind == ApiIssueKind.DUPLICATE_ROUTE })
    }

    @Test
    fun preflightSeesKeyOverwriteAndRouteConflictsBeforeImport() {
        val base = Api { post<Login, LoggedIn>(Login::class, "login") }
        val other = Api {
            patch<Login, LoggedIn>(Login::class, "another")
            post<Query, User>(PathLookup::class, "/login")
        }
        val issues = base.conflictsWith(other)
        assertTrue(issues.any { it.kind == ApiIssueKind.DUPLICATE_KEY })
        assertTrue(issues.any { it.kind == ApiIssueKind.DUPLICATE_ROUTE })
        // Original behavior remains unchanged; diagnostics are opt-in.
        base.include(other)
        assertEquals("PATCH", base[Login::class]?.method)
    }

    @Test
    fun malformedOrRepeatedPlaceholdersAreReportedWithoutChangingRegistration() {
        val api = Api {
            get<Query, User>(ReadUser::class, "users/{invalid-name}")
            get<Query, User>(ReadRole::class, "users/{userId}/roles/{userId}")
        }
        assertEquals(2, api.validateRoutes().count { it.kind == ApiIssueKind.INVALID_PATH_PARAMETER })
        assertEquals("users/{invalid-name}", api[ReadUser::class]?.url)
    }

    @Test
    fun checkedReaderRejectsWrongRawTypesButOriginalReaderStillExists() {
        val api = Api {
            post<Login, LoggedIn>(Login::class, "login")
        }
        val checked: Reader<Api, EndPoint<Login, LoggedIn>> = CheckedEndPoint(Login::class)
        assertEquals(api[Login::class], checked(api))
        assertFailsWith<IllegalArgumentException> {
            CheckedEndPoint<Login, OtherResponse>(Login::class)(api)
        }
        assertFailsWith<IllegalArgumentException> {
            CheckedEndPoint<Login, LoggedIn>(ReadUser::class)(api)
        }
        val legacy: Reader<Api, EndPoint<Login, LoggedIn>> = EndPoint(Login::class)
        assertEquals(api[Login::class], legacy(api))
    }

    @Test
    fun groupRemainsPureMetadataForTemplatedRoutes() {
        val api = Api {
            group("users") {
                get<Query, User>(ReadUser::class, "users/{userId}")
            }
        }
        assertEquals("users/{userId}", api[ReadUser::class]?.url)
        assertEquals("users", api[ReadUser::class]?.group)
    }
}
