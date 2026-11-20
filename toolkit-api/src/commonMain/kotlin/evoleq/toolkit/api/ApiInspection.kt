package evoleq.toolkit.api

import evoleq.toolkit.core.reader.Reader
import kotlin.reflect.KClass

/** Optional diagnostics: existing Api builders keep their overwrite behavior. */
enum class ApiIssueKind {
    DUPLICATE_KEY,
    DUPLICATE_ROUTE,
    INVALID_PATH_PARAMETER
}

/**
 * A diagnostic, never a new registration policy. EndPoint metadata and the
 * existing KClass key remain the source of truth.
 */
data class ApiIssue(
    val kind: ApiIssueKind,
    val key: KClass<*>,
    val conflictingKey: KClass<*>? = null,
    val detail: String
)

/**
 * Validate current entries after assembling an API. A key overwritten earlier
 * is no longer present in the Map, so this method cannot recover that history.
 */
fun Api.validateRoutes(): List<ApiIssue> {
    val issues = mutableListOf<ApiIssue>()
    val entries = entries.toList()
    entries.forEach { (key, endpoint) ->
        issues += invalidParameters(key, endpoint.url)
    }
    for (i in entries.indices) {
        for (j in i + 1 until entries.size) {
            val (firstKey, first) = entries[i]
            val (secondKey, second) = entries[j]
            if (sameRoute(first, second)) {
                issues += ApiIssue(
                    kind = ApiIssueKind.DUPLICATE_ROUTE,
                    key = firstKey,
                    conflictingKey = secondKey,
                    detail = "Both endpoint keys match " + first.method + " " + first.url
                )
            }
        }
    }
    return issues
}

/**
 * Preflight diagnostics to call *before* include, group or module. In
 * particular it reports collisions on a KClass key before a mutable Map
 * overwrites the existing endpoint. The caller chooses what to do.
 */
fun Api.conflictsWith(fragment: Api): List<ApiIssue> {
    val issues = mutableListOf<ApiIssue>()
    for ((incomingKey, incoming) in fragment.endPoints) {
        issues += invalidParameters(incomingKey, incoming.url)
        if (containsKey(incomingKey)) {
            issues += ApiIssue(
                kind = ApiIssueKind.DUPLICATE_KEY,
                key = incomingKey,
                conflictingKey = incomingKey,
                detail = "Endpoint key is already registered: " + incomingKey
            )
        }
        for ((existingKey, existing) in endPoints) {
            if (sameRoute(existing, incoming)) {
                issues += ApiIssue(
                    kind = ApiIssueKind.DUPLICATE_ROUTE,
                    key = existingKey,
                    conflictingKey = incomingKey,
                    detail = "Routes overlap: " + existing.method + " " + existing.url +
                        " and " + incoming.method + " " + incoming.url
                )
            }
        }
    }
    return issues
}

/**
 * Preserves the original EndPoint(key) reader and supplies an opt-in checked
 * alternative. KClass checks verify raw classes, not erased generic arguments
 * such as List<User> versus List<Other>.
 */
@Suppress("FunctionName")
inline fun <reified S, reified T> CheckedEndPoint(
    key: KClass<*>
): Reader<Api, EndPoint<S, T>> = Reader { api ->
    val value = requireNotNull(api[key]) { "No endpoint for key " + key }
    require(value.requestType == S::class && value.responseType == T::class) {
        "Type mismatch for key " + key + ": declared " +
            value.requestType + " -> " + value.responseType +
            ", requested " + S::class + " -> " + T::class
    }
    @Suppress("UNCHECKED_CAST")
    (value as EndPoint<S, T>)
}

private fun sameRoute(a: EndPoint<*, *>, b: EndPoint<*, *>): Boolean =
    a.method == b.method && pathShape(a.url) == pathShape(b.url)

/** Ignore irrelevant slashes and parameter *names* only in diagnostics. */
private fun pathShape(path: String): String =
    path.split('/').filter(String::isNotEmpty).joinToString("/") { segment ->
        if (parameterName(segment) != null) "{}" else segment
    }

private fun parameterName(segment: String): String? {
    if (!segment.startsWith("{") || !segment.endsWith("}")) return null
    val name = segment.substring(1, segment.length - 1)
    if (name.isEmpty() || !(name[0].isLetter() || name[0] == '_')) return null
    return if (name.all { it.isLetterOrDigit() || it == '_' }) name else null
}

private fun invalidParameters(key: KClass<*>, path: String): List<ApiIssue> {
    val issues = mutableListOf<ApiIssue>()
    val seenNames = mutableSetOf<String>()
    for (segment in path.split('/').filter(String::isNotEmpty)) {
        val isTemplate = '{' in segment || '}' in segment
        if (!isTemplate) continue
        val name = parameterName(segment)
        if (name == null) {
            issues += ApiIssue(
                kind = ApiIssueKind.INVALID_PATH_PARAMETER,
                key = key,
                detail = "Invalid path parameter segment: " + segment + " in " + path
            )
        } else if (!seenNames.add(name)) {
            issues += ApiIssue(
                kind = ApiIssueKind.INVALID_PATH_PARAMETER,
                key = key,
                detail = "Repeated path parameter name: " + name + " in " + path
            )
        }
    }
    return issues
}
