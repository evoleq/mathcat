package evoleq.toolkit.api

import evoleq.toolkit.core.reader.Reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ApiCompositionTest {
    private class Login
    private class LoggedIn
    private class CreateUser
    private class User
    private class ReadUsers : EmptyParams()
    private class UserList
    private class BuildInfo
    private class PutKey
    private class PatchKey
    private class DeleteKey
    private class HeadKey

    @Test
    fun originalApiDslAndMapContractRemainAvailable() {
        class Key1
        class Key2

        val api = Api {
            post<Int, String>(key = Key1::class, url = "test-url-1")
            get<Parameters, String>(key = Key2::class, url = "test-url-2")
        }

        assertEquals(2, api.keys.size)
        assertIs<EndPoint.Post<Int, String>>(api[Key1::class])
        assertIs<EndPoint.Get<Parameters, String>>(api[Key2::class])
        assertEquals("test-url-1", api[Key1::class]?.url)
    }

    @Test
    fun reifiedKeyOverloadsRemainAvailable() {
        val api = Api {
            post<Login, Login, LoggedIn>("login")
            get<ReadUsers, ReadUsers, UserList>("users", parameters = setOf("page"))
            put<PutKey, CreateUser, User>("create")
            patch<PatchKey, CreateUser, User>("update")
            delete<DeleteKey, CreateUser, User>("delete")
            head<HeadKey, Unit, Unit>("status")
        }

        assertEquals("POST", api[Login::class]?.method)
        assertEquals(setOf("page"), api[ReadUsers::class]?.parameters)
        assertEquals("PUT", api[PutKey::class]?.method)
        assertEquals("PATCH", api[PatchKey::class]?.method)
        assertEquals("DELETE", api[DeleteKey::class]?.method)
        assertEquals("HEAD", api[HeadKey::class]?.method)
    }

    @Test
    fun fragmentCompositionMatchesTheExistingInlineDsl() {
        val inline = Api {
            group("authentication") {
                post<Login, LoggedIn>(Login::class, "login")
            }
            module("users") {
                post<CreateUser, User>(CreateUser::class, "create")
                get<ReadUsers, UserList>(ReadUsers::class, "all")
            }
            head<Unit, BuildInfo>(BuildInfo::class, "api/version")
        }

        val auth = Api { post<Login, LoggedIn>(Login::class, "login") }
        val users = Api {
            post<CreateUser, User>(CreateUser::class, "create")
            get<ReadUsers, UserList>(ReadUsers::class, "all")
        }
        val global = Api { head<Unit, BuildInfo>(BuildInfo::class, "api/version") }

        val fromModules = Api {
            group("authentication", auth)
            module("users", users)
            include(global)
        }

        assertEquals(inline.endPoints, fromModules.endPoints)
        assertEquals("authentication", fromModules[Login::class]?.group)
        assertEquals("login", fromModules[Login::class]?.url)
        assertEquals("users", fromModules[CreateUser::class]?.group)
        assertEquals("users/create", fromModules[CreateUser::class]?.url)
        assertEquals("api/version", fromModules[BuildInfo::class]?.url)
    }

    @Test
    fun existingModuleOverloadAlreadyAcceptsApiFromOtherKotlinModules() {
        val accounts = Api { post<CreateUser, User>(CreateUser::class, "create") }
        val app = Api { module("users", accounts) }
        assertEquals("users/create", app[CreateUser::class]?.url)
        assertEquals("create", accounts[CreateUser::class]?.url)
        assertEquals(null, accounts[CreateUser::class]?.group)
    }

    @Test
    fun originalNestedGroupAndModuleBehaviorIsUnchanged() {
        val api = Api {
            group("organization") {
                module("members") {
                    post<CreateUser, User>(CreateUser::class, "add")
                }
            }
        }
        // The surrounding legacy group rewrites group metadata; the module
        // still contributes its path prefix, as before.
        assertEquals("organization", api[CreateUser::class]?.group)
        assertEquals("members/add", api[CreateUser::class]?.url)
    }

    @Test
    fun includeDoesNotPrefixOrRegroupImportedEndpoints() {
        val auth = Api {
            group("authentication") {
                post<Login, LoggedIn>(Login::class, "login")
            }
        }
        val app = Api { include(auth) }
        assertEquals(auth.endPoints, app.endPoints)
        assertEquals("authentication", app[Login::class]?.group)
    }

    @Test
    fun importedFragmentsKeepTheHistoricalKeyOverwritePolicy() {
        val old = Api { post<Login, LoggedIn>(Login::class, "login") }
        val replacement = Api { patch<Login, LoggedIn>(Login::class, "refresh-login") }
        val api = Api { include(old); include(replacement) }
        assertEquals("PATCH", api[Login::class]?.method)
        assertEquals("refresh-login", api[Login::class]?.url)
        assertEquals("login", old[Login::class]?.url)
    }

    @Test
    fun endpointReaderRemainsAReaderOfTheApiMap() {
        val api = Api { post<Login, LoggedIn>(Login::class, "login") }
        val reader: Reader<Api, EndPoint<Login, LoggedIn>> = EndPoint(Login::class)
        assertEquals(api[Login::class], reader(api))
        assertNotNull(reader(api))
    }
}
