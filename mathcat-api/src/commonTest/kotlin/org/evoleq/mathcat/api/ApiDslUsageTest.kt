package org.evoleq.mathcat.api

import org.evoleq.mathcat.core.reader.Reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Executable usage examples for the original Evoleq DSL.
 *
 * The tests are intentionally small and readable: copy the Api { ... } blocks
 * into a feature or composition root. None of these examples requires Ktor.
 */
class ApiDslUsageTest {
    private class Login
    private class LoggedIn
    private class RefreshToken
    private class ReadUser : EmptyParams()
    private class User
    private class CreateUser
    private class ReadPermissions : EmptyParams()
    private class Permissions
    private class Version
    private class VersionInfo
    private class DifferentResponse

    /** 1. Original keyed syntax; Api is still a Map keyed by KClass. */
    @Test
    fun declareEndpointsWithTheExistingApiBlock() {
        val api = Api {
            post<Login, LoggedIn>(key = Login::class, url = "login")
            get<ReadUser, User>(
                key = ReadUser::class,
                url = "users/{userId}",
                parameters = setOf("expand")
            )
        }

        assertEquals(setOf(Login::class, ReadUser::class), api.keys.toSet())
        assertIs<EndPoint.Post<Login, LoggedIn>>(api[Login::class])
        assertIs<EndPoint.Get<ReadUser, User>>(api[ReadUser::class])
        assertEquals("POST", api[Login::class]?.method)
        assertEquals("users/{userId}", api[ReadUser::class]?.url)
        assertEquals(ReadUser::class, api[ReadUser::class]?.requestType)
        assertEquals(User::class, api[ReadUser::class]?.responseType)
        assertEquals(setOf("expand"), api[ReadUser::class]?.parameters)
    }

    /** 2. The existing reified-key overload removes the explicit key argument. */
    @Test
    fun declareEndpointsUsingReifiedKeys() {
        val api = Api {
            post<Login, Login, LoggedIn>("login")
            patch<CreateUser, CreateUser, User>("users/update")
        }

        assertEquals("login", api[Login::class]?.url)
        assertEquals("PATCH", api[CreateUser::class]?.method)
    }

    /** 3. group is a label, not a URL prefix. */
    @Test
    fun useAGroupToOrganizeAnApiWithoutChangingUrls() {
        val api = Api {
            group("authentication") {
                post<Login, LoggedIn>(Login::class, "login")
                post<RefreshToken, LoggedIn>(RefreshToken::class, "refresh")
            }
        }

        assertEquals("authentication", api[Login::class]?.group)
        assertEquals("authentication", api[RefreshToken::class]?.group)
        assertEquals("login", api[Login::class]?.url)
        assertEquals("refresh", api[RefreshToken::class]?.url)
    }

    /**
     * 4. module mounts URL prefixes, including Ktor-style {param} placeholders.
     * Existing group metadata reflects the *outermost* module path.
     */
    @Test
    fun nestModulesAndDeclareNamedPathParameters() {
        val api = Api {
            module("api/v1") {
                module("users/") {
                    get<ReadUser, User>(
                        key = ReadUser::class,
                        url = "/{userId}",
                        parameters = setOf("expand")
                    )
                }
            }
        }

        assertEquals("api/v1/users/{userId}", api[ReadUser::class]?.url)
        assertEquals("api/v1", api[ReadUser::class]?.group)
        assertEquals(setOf("expand"), api[ReadUser::class]?.parameters)
        assertTrue(api.validateRoutes().isEmpty())
    }

    /**
     * 5. A composition root can collect independent Api values from features.
     * module(path, Api) existed in the original DSL. group(name, Api) and
     * include(Api) are its small modularity extensions.
     */
    @Test
    fun assembleSeparatelyDeclaredFeatureApis() {
        val authApi = Api { post<Login, LoggedIn>(Login::class, "login") }
        val usersApi = Api { post<CreateUser, User>(CreateUser::class, "create") }
        val versionApi = Api { head<Version, VersionInfo>(Version::class, "api/version") }

        val applicationApi = Api {
            group("authentication", authApi)
            module("users", usersApi)
            include(versionApi)
        }

        assertEquals("login", applicationApi[Login::class]?.url)
        assertEquals("authentication", applicationApi[Login::class]?.group)
        assertEquals("users/create", applicationApi[CreateUser::class]?.url)
        assertEquals("users", applicationApi[CreateUser::class]?.group)
        assertEquals("api/version", applicationApi[Version::class]?.url)
        assertNull(applicationApi[Version::class]?.group)

        // Mounting copies descriptors instead of editing the feature Api.
        assertEquals("create", usersApi[CreateUser::class]?.url)
    }

    /** 6. Read endpoints through the established Reader, or opt into a checked one. */
    @Test
    fun readAnEndpointWithTheExistingAndCheckedReader() {
        val api = Api { post<Login, LoggedIn>(Login::class, "login") }

        val original: Reader<Api, EndPoint<Login, LoggedIn>> = EndPoint(Login::class)
        val checked: Reader<Api, EndPoint<Login, LoggedIn>> = CheckedEndPoint(Login::class)

        assertEquals(api[Login::class], original(api))
        assertEquals(original(api), checked(api))
        assertFailsWith<IllegalArgumentException> {
            CheckedEndPoint<Login, DifferentResponse>(Login::class)(api)
        }
    }

    /**
     * 7. Diagnostics are opt-in. They flag matching route shapes before
     * composition without changing the original Map/overwrite behavior.
     */
    @Test
    fun inspectPotentialRouteCollisionsWithoutChangingRegistration() {
        val users = Api { get<ReadUser, User>(ReadUser::class, "users/{id}") }
        val permissions = Api {
            get<ReadPermissions, Permissions>(ReadPermissions::class, "/users/{userId}/")
        }

        assertTrue(users.conflictsWith(permissions).any {
            it.kind == ApiIssueKind.DUPLICATE_ROUTE
        })

        val applicationApi = Api {
            include(users)
            include(permissions)
        }
        assertEquals(2, applicationApi.size)
        assertEquals(1, applicationApi.validateRoutes().count {
            it.kind == ApiIssueKind.DUPLICATE_ROUTE
        })
    }

    /** 8. The legacy KClass registry still overwrites a repeated key. */
    @Test
    fun checkKeyConflictsBeforeKeepingTheLegacyOverwriteSemantics() {
        val original = Api { get<ReadUser, User>(ReadUser::class, "users/all") }
        val replacement = Api { delete<ReadUser, User>(ReadUser::class, "users/delete") }

        assertTrue(original.conflictsWith(replacement).any {
            it.kind == ApiIssueKind.DUPLICATE_KEY
        })
        original.include(replacement)
        assertEquals("DELETE", original[ReadUser::class]?.method)
        assertEquals("users/delete", original[ReadUser::class]?.url)
        assertEquals("DELETE", replacement[ReadUser::class]?.method)
    }
}
