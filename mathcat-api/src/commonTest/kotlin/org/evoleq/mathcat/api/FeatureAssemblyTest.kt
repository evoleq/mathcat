package org.evoleq.mathcat.api

import org.evoleq.mathcat.api.fixtures.authentication.Login
import org.evoleq.mathcat.api.fixtures.authentication.LoggedIn
import org.evoleq.mathcat.api.fixtures.authentication.RefreshToken
import org.evoleq.mathcat.api.fixtures.authentication.authenticationApi
import org.evoleq.mathcat.api.fixtures.users.ReadUser
import org.evoleq.mathcat.api.fixtures.users.User
import org.evoleq.mathcat.api.fixtures.users.CreateUser
import org.evoleq.mathcat.api.fixtures.users.usersApi
import org.evoleq.mathcat.api.fixtures.banking.ReadAccount
import org.evoleq.mathcat.api.fixtures.banking.Account
import org.evoleq.mathcat.api.fixtures.banking.bankingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Realistic independent feature packages. A separate Gradle-module
 * integration project may be added when consumers need that proof.
 */
class FeatureAssemblyTest {
    @Test
    fun modularAssemblyMatchesOriginalInlineDslExactly() {
        val inline = Api {
            group("authentication") {
                post<Login, LoggedIn>(Login::class, "login")
                post<RefreshToken, LoggedIn>(RefreshToken::class, "refresh")
            }
            module("users") {
                get<ReadUser, User>(ReadUser::class, "{userId}", parameters = setOf("expand"))
                post<CreateUser, User>(CreateUser::class, "create")
            }
            module("banking") {
                get<EmptyParams, Account>(ReadAccount::class, "accounts/{accountId}")
            }
        }
        val composed = Api {
            group("authentication", authenticationApi)
            module("users", usersApi)
            module("banking", bankingApi)
        }

        assertEquals(inline.endPoints, composed.endPoints)
        assertEquals("users/{userId}", composed[ReadUser::class]?.url)
        assertEquals("banking/accounts/{accountId}", composed[ReadAccount::class]?.url)
        assertEquals("authentication", composed[Login::class]?.group)
        assertTrue(composed.validateRoutes().isEmpty())
    }

    @Test
    fun slashTolerantPathJoinDoesNotRewriteLegacyGroupMetadata() {
        // Route URLs are intentionally equivalent, but the legacy group
        // records the literal module argument. Both facts must be tested.
        val noTrailingSlash = Api { module("users", usersApi) }
        val withTrailingSlash = Api { module("users/", usersApi) }

        assertEquals(noTrailingSlash[ReadUser::class]?.url, withTrailingSlash[ReadUser::class]?.url)
        assertEquals("users", noTrailingSlash[ReadUser::class]?.group)
        assertEquals("users/", withTrailingSlash[ReadUser::class]?.group)
        assertEquals("users/{userId}", withTrailingSlash[ReadUser::class]?.url)
    }

    @Test
    fun mountingIndependentFeaturesAtDifferentPathsDoesNotModifyThem() {
        val v1 = Api { module("v1", usersApi); module("v1/banking", bankingApi) }
        val v2 = Api { module("v2", usersApi); module("v2/banking", bankingApi) }

        assertEquals("v1/{userId}", v1[ReadUser::class]?.url)
        assertEquals("v2/{userId}", v2[ReadUser::class]?.url)
        assertEquals("{userId}", usersApi[ReadUser::class]?.url)
        assertEquals("accounts/{accountId}", bankingApi[ReadAccount::class]?.url)
    }
}
