package org.evoleq.mathcat.ktorx

import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.evoleq.mathcat.api.Api
import kotlin.reflect.KClass

/**
 * Route an original Api endpoint using *its declared* method and path.
 *
 * [createBase] is invoked for **each request**. An application may use its
 * existing Base(call, environment) function and composed Action workflow.
 * No database, transaction, authorization or serializer policy is invented.
 */
fun <Environment, Base> Route.bindEndpoint(
    api: Api,
    key: KClass<*>,
    environment: Environment,
    createBase: (ApplicationCall, Environment) -> Base,
    handler: suspend (Base) -> Unit
) {
    val endpoint = requireNotNull(api[key]) { "Unknown endpoint key: " + key }
    route(endpoint.url, HttpMethod.parse(endpoint.method)) {
        handle {
            handler(createBase(call, environment))
        }
    }
}

/** Minimal Ktor-only binding for handlers that only need ApplicationCall. */
fun Route.bindEndpoint(
    api: Api,
    key: KClass<*>,
    handler: suspend (ApplicationCall) -> Unit
) = bindEndpoint(api, key, Unit, { call, _ -> call }, handler)
