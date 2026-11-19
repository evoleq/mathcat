package org.evoleq.mathcat.ktorx

import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.validateRoutes
import org.evoleq.mathcat.result.ExceptionTransforms
import org.evoleq.mathcat.result.Result
import org.evoleq.mathcat.serialization.Serializers
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Request-local Ktor context, not the domain's mathematical Boundary.
 * Environment capabilities are supplied by the application composition root.
 */
data class RequestContext<out E>(
    val call: ApplicationCall,
    val environment: E
)

/**
 * An opt-in route builder for the existing Api DSL.
 *
 * KtorIO and composed serializers are configured once at installation, not
 * separately for every route. No request objects or state are cached.
 */
class ApiRoutes<E> internal constructor(
    private val route: Route,
    private val api: Api,
    private val environment: E,
    private val io: KtorIO<RequestContext<E>>
) {
    private val registered = mutableSetOf<KClass<*>>()

    private fun register(key: KClass<*>) {
        requireNotNull(api[key]) { "Unknown API endpoint key: " + key }
        require(registered.add(key)) { "Duplicate API handler for endpoint key: " + key }
    }

    /**
     * Default request scope plus the original Receive * Transform * Respond
     * operators exposed through the KtorIO receiver.
     */
    fun handle(
        key: KClass<*>,
        handler: suspend KtorIO<RequestContext<E>>.(RequestContext<E>) -> Unit
    ) {
        register(key)
        route.bindEndpoint(api, key, environment, ::RequestContext) { context ->
            io.handler(context)
        }
    }

    /** Keep application-owned Base types as an explicit opt-in alternative. */
    fun <B> handle(
        key: KClass<*>,
        createBase: (ApplicationCall, E) -> B,
        handler: suspend (B) -> Unit
    ) {
        register(key)
        route.bindEndpoint(api, key, environment, createBase, handler)
    }
}

/**
 * Bind handlers by endpoint key, deriving the method/path exclusively from Api.
 * `serializers` accepts manual, KSP-generated or composed feature registries.
 * All diagnostics are opt-in: the original Api builder is not modified.
 */
fun <E> Route.installApi(
    api: Api,
    environment: E,
    serializers: Serializers,
    json: Json = Json,
    contextHeader: String = CONTEXT_HEADER,
    principal: suspend (ApplicationCall) -> Result<UUID> = ::jwtUserId,
    transforms: ExceptionTransforms = ExceptionTransforms { },
    configure: ApiRoutes<E>.() -> Unit
) {
    val issues = api.validateRoutes()
    require(issues.isEmpty()) {
        "Invalid Api routes:\n" + issues.joinToString("\n") { issue ->
            issue.kind.toString() + ": " + issue.detail
        }
    }
    val io = KtorIO<RequestContext<E>>(
        callOf = { it.call },
        serializers = serializers,
        json = json,
        contextHeader = contextHeader,
        principal = principal,
        transforms = transforms
    )
    ApiRoutes(this, api, environment, io).configure()
}
