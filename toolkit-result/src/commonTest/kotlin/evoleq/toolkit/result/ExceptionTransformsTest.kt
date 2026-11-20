package evoleq.toolkit.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExceptionTransformsTest {
    private class Unauthenticated : IllegalStateException("expired token")
    private class MissingAccount : IllegalStateException("bank account missing")
    private class PermissionDenied : IllegalStateException("not allowed")

    private val authentication = ExceptionTransforms {
        on<Unauthenticated>(401) { "unauthorized: " + it.message }
    }
    private val banking = ExceptionTransforms {
        on<MissingAccount>(404)
    }

    @Test
    fun modulesProvideIndependentRulesAndApplicationComposesThem() {
        val application = ExceptionTransforms {
            include(authentication)
            include(banking)
            on<PermissionDenied>(403)
        }

        assertEquals(ErrorResponse(401, Result.Failure.Message("unauthorized: expired token")),
            application.transform(Result.Failure.Exception(Unauthenticated())))
        assertEquals(ErrorResponse(404, Result.Failure.Message("bank account missing")),
            application.transform(Result.Failure.Exception(MissingAccount())))
        assertEquals(ErrorResponse(403, Result.Failure.Message("not allowed")),
            application.transform(Result.Failure.Exception(PermissionDenied())))
    }

    @Test
    fun fallbackIsExplicitAndDoesNotExposeExceptionTypesAsProtocol() {
        val application = ExceptionTransforms {
            include(authentication)
            otherwise(500) { "internal error" }
        }
        assertEquals(ErrorResponse(500, Result.Failure.Message("internal error")),
            application.transform(Result.Failure.Exception(IllegalArgumentException("secret"))))
    }

    @Test
    fun duplicateExactExceptionMappingsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            ExceptionTransforms {
                include(authentication)
                on<Unauthenticated>(403)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            ExceptionTransforms {
                include(banking)
                include(banking)
            }
        }
    }

    @Test
    fun firstMatchingRuleWinsForExceptionSubclasses() {
        val policy = ExceptionTransforms {
            on<Unauthenticated>(401)
            on<IllegalStateException>(409)
        }
        assertEquals(401,
            policy.transform(Result.Failure.Exception(Unauthenticated())).statusCode)
        assertEquals(409,
            policy.transform(Result.Failure.Exception(IllegalStateException())).statusCode)
    }

    @Test
    fun statusCodesAreValidatedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            ExceptionTransforms { on<Unauthenticated>(0) }
        }
    }
}
