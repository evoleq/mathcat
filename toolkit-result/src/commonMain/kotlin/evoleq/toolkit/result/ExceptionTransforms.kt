package evoleq.toolkit.result

import kotlin.reflect.KClass

/**
 * Framework-free outcome of converting an application exception to an error.
 * The Ktor adapter is responsible for producing an HttpStatusCode.
 */
data class ErrorResponse(
    val statusCode: Int,
    val message: Result.Failure.Message
)

/**
 * A composable, ordered exception policy.
 *
 * A module can define its own policies and a composition root may include
 * them. First matching rule wins; duplicate *exact* classes are rejected.
 * The original fallback is HTTP 500 with the exception message.
 */
class ExceptionTransforms private constructor(
    private val rules: List<Rule>,
    private val fallbackStatus: Int,
    private val fallbackMessage: (Throwable) -> String
) {
    private class Rule(
        val exceptionClass: KClass<out Throwable>,
        val map: (Throwable) -> ErrorResponse?
    )

    fun transform(error: Result.Failure.Exception): ErrorResponse {
        val exception = error.value
        for (rule in rules) {
            val mapped = rule.map(exception)
            if (mapped != null) return mapped
        }
        return ErrorResponse(fallbackStatus, Result.Failure.Message(fallbackMessage(exception)))
    }

    class Builder {
        private val rules = mutableListOf<Rule>()
        private val classes = mutableSetOf<KClass<out Throwable>>()
        private var fallbackStatus = 500
        private var fallbackMessage: (Throwable) -> String = {
            it.message ?: "No message provided"
        }

        /**
         * A specific rule: the provided class may also match its subclasses.
         * Rules are evaluated in insertion order, so add specialized rules
         * before broad ones.
         */
        fun <E : Throwable> on(
            type: KClass<E>,
            statusCode: Int,
            message: (E) -> String = { it.message ?: "No message provided" }
        ) {
            require(statusCode in 100..599) { "Invalid HTTP status code: $statusCode" }
            require(classes.add(type)) {
                "Duplicate exception rule for " + type
            }
            rules += Rule(type) { throwable ->
                if (type.isInstance(throwable)) {
                    @Suppress("UNCHECKED_CAST")
                    val failure = throwable as E
                    ErrorResponse(statusCode, Result.Failure.Message(message(failure)))
                } else null
            }
        }

        inline fun <reified E : Throwable> on(
            statusCode: Int,
            noinline message: (E) -> String = { it.message ?: "No message provided" }
        ) = on(E::class, statusCode, message)

        /** Include another module's rules in their original relative order. */
        fun include(fragment: ExceptionTransforms) {
            for (rule in fragment.rules) {
                require(classes.add(rule.exceptionClass)) {
                    "Duplicate exception rule for " + rule.exceptionClass + " while including"
                }
                rules += rule
            }
        }

        /** Explicit, safe fallback for unmapped application errors. */
        fun otherwise(
            statusCode: Int = 500,
            message: (Throwable) -> String = { it.message ?: "No message provided" }
        ) {
            require(statusCode in 100..599) { "Invalid HTTP status code: $statusCode" }
            fallbackStatus = statusCode
            fallbackMessage = message
        }

        internal fun build(): ExceptionTransforms = ExceptionTransforms(
            rules.toList(), fallbackStatus, fallbackMessage
        )
    }

    companion object {
        operator fun invoke(configure: Builder.() -> Unit): ExceptionTransforms =
            Builder().apply(configure).build()
    }
}
