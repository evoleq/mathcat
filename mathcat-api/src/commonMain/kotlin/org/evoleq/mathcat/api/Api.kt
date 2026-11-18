package org.evoleq.mathcat.api

/** HTTP vocabulary, deliberately independent of server/client frameworks. */
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }

/**
 * Typed declaration handle. Use the *same instance* when declaring and binding a route.
 *
 * Type parameters are checked by Kotlin at call sites; they are not runtime codecs.
 * Reference identity prevents an unrelated same-named declaration from being bound
 * with mismatched request/response types.
 */
class Endpoint<Request : Any, Response : Any>(
    val id: String,
    val method: HttpMethod,
    path: String
) {
    init {
        require(id.isNotBlank() && id == id.trim() && id.none(Char::isWhitespace)) {
            "Endpoint id must be nonblank and contain no whitespace"
        }
    }

    val path: String = normalizePath(path)

    override fun toString(): String = "Endpoint($id, $method $path)"
}

/** The original module remains visible even when mounted below several parents. */
data class EndpointOrigin(
    val moduleId: String,
    val groups: List<String> = emptyList()
)

/** Framework-neutral description of a mounted endpoint. */
data class EndpointDescriptor internal constructor(
    val endpoint: Endpoint<*, *>,
    val path: String,
    val origin: EndpointOrigin
) {
    val id: String get() = endpoint.id
    val method: HttpMethod get() = endpoint.method
}

/** A typed binding returned only for the exact declaration handle in an API. */
data class ResolvedEndpoint<Request : Any, Response : Any>(
    val endpoint: Endpoint<Request, Response>,
    val path: String,
    val origin: EndpointOrigin
) {
    val method: HttpMethod get() = endpoint.method
}

/** Immutable result of validating a compositional API declaration. */
class Api internal constructor(
    val id: String,
    entries: List<EndpointDescriptor>
) {
    private val records = entries.toList()

    /** Copy-on-read avoids exposing the builder's mutable collection. */
    val endpoints: List<EndpointDescriptor> get() = records.toList()

    /**
     * No unchecked cast: metadata is looked up by referential identity, while the
     * caller's already-typed Endpoint supplies Request and Response.
     */
    fun <Request : Any, Response : Any> resolve(
        endpoint: Endpoint<Request, Response>
    ): ResolvedEndpoint<Request, Response>? =
        records.firstOrNull { it.endpoint === endpoint }?.let { found ->
            ResolvedEndpoint(endpoint, found.path, found.origin)
        }

    fun <Request : Any, Response : Any> requireEndpoint(
        endpoint: Endpoint<Request, Response>
    ): ResolvedEndpoint<Request, Response> =
        requireNotNull(resolve(endpoint)) { "Endpoint '" + endpoint.id + "' is not part of API '$id'" }
}

/**
 * A small mutable *construction* DSL; the built Api is a validated snapshot.
 * group adds logical metadata. module prefixes the path but retains the
 * contributing API's original module identity.
 */
class ApiBuilder internal constructor(private val moduleId: String) {
    private val declarations = mutableListOf<EndpointDescriptor>()

    fun <Request : Any, Response : Any> endpoint(value: Endpoint<Request, Response>) {
        declarations += EndpointDescriptor(value, value.path, EndpointOrigin(moduleId))
    }

    fun group(name: String, configure: ApiBuilder.() -> Unit) {
        requireName(name, "Group")
        val nested = ApiBuilder(moduleId).apply(configure)
        declarations += nested.declarations.map { entry ->
            entry.copy(origin = entry.origin.copy(groups = listOf(name) + entry.origin.groups))
        }
    }

    /** A fragment may be assembled in another Gradle module, without Ktor. */
    fun module(prefix: String, fragment: Api) {
        val mount = normalizePath(prefix)
        declarations += fragment.endpoints.map { entry ->
            entry.copy(path = joinPaths(mount, entry.path))
        }
    }

    internal fun build(): Api {
        // Validate at assembly time, before returning anything to the consumer.
        val identities = mutableSetOf<Pair<String, String>>()
        val routes = mutableSetOf<Pair<HttpMethod, String>>()
        val handles = mutableSetOf<Endpoint<*, *>>()
        for (entry in declarations) {
            require(identities.add(entry.origin.moduleId to entry.id)) {
                "Duplicate endpoint '" + entry.origin.moduleId + ":" + entry.id + "' in API '$moduleId'"
            }
            require(handles.add(entry.endpoint)) {
                "The same Endpoint instance '" + entry.id + "' was mounted more than once"
            }
            require(routes.add(entry.method to entry.path)) {
                "Duplicate route '" + entry.method + " " + entry.path + "' in API '$moduleId'"
            }
        }
        return Api(moduleId, declarations)
    }
}

fun api(id: String, configure: ApiBuilder.() -> Unit): Api {
    requireName(id, "API module")
    return ApiBuilder(id).apply(configure).build()
}

private fun requireName(name: String, kind: String) {
    require(name.isNotBlank() && name == name.trim() && name.none(Char::isWhitespace)) {
        "$kind id must be nonblank and contain no whitespace"
    }
}

/** Canonical absolute URL path; supports named parameters such as {id}. */
internal fun normalizePath(path: String): String {
    require('?' !in path && '#' !in path) { "Query and fragment are not part of an endpoint path: '$path'" }
    val segments = path.split('/').filter(String::isNotEmpty)
    require(segments.none { it == "." || it == ".." || it.any(Char::isWhitespace) }) {
        "Invalid path segment in '$path'"
    }
    return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
}

private fun joinPaths(prefix: String, path: String): String = normalizePath("$prefix/$path")
