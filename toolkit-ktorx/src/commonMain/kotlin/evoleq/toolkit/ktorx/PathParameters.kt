package evoleq.toolkit.ktorx

import io.ktor.http.encodeURLPathPart

/**
 * Resolve a declared Ktor-style route template without changing Api or EndPoint.
 *
 * Values are encoded as *one path segment*, not interpolated as raw URL text.
 * Query parameters are handled by Ktor's UrlBuilder independently.
 */
fun resolvePathParameters(
    declaredPath: String,
    parameters: Map<String, String>
): String {
    val placeholder = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
    val names = placeholder.findAll(declaredPath).map { it.groupValues[1] }.toSet()
    require(parameters.keys == names) {
        "Path parameters mismatch: expected " + names + ", supplied " + parameters.keys
    }
    val resolved = placeholder.replace(declaredPath) { match ->
        requireNotNull(parameters[match.groupValues[1]]).encodeURLPathPart()
    }
    require('{' !in resolved && '}' !in resolved) {
        "Unresolved or malformed template in " + declaredPath
    }
    return resolved
}
