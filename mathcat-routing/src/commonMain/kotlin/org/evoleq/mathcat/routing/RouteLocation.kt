package org.evoleq.mathcat.routing

/** Query pairs retain duplicates and ordering; they are not a Map. */
data class Parameter(val key: String, val value: String)

data class RouteLocation(
    val segments: List<String>,
    val queryParameters: List<Parameter> = emptyList(),
    val fragment: String? = null
) {
    val query: Map<String, List<String>>
        get() = queryParameters.groupBy({ it.key }, { it.value })

    fun toUrl(): String = buildString {
        append('/')
        append(segments.joinToString("/") { encodeComponent(it) })
        if (queryParameters.isNotEmpty()) {
            append('?')
            append(queryParameters.joinToString("&") {
                "${encodeComponent(it.key)}=${encodeComponent(it.value)}"
            })
        }
        if (fragment != null) {
            append('#')
            append(encodeComponent(fragment))
        }
    }
}

sealed interface RouteFailure {
    data class MalformedUrl(val message: String) : RouteFailure
    data class DuplicateName(val name: String) : RouteFailure
    data class AmbiguousPattern(val first: String, val second: String) : RouteFailure
    data class DuplicateParameter(val name: String) : RouteFailure
    data class MissingName(val name: String) : RouteFailure
    data class MissingParameter(val name: String) : RouteFailure
    data class UnexpectedParameter(val name: String) : RouteFailure
    data class NotFound(val path: String) : RouteFailure
    data class AccessDenied(val route: String) : RouteFailure
}

/** Parse a browser-local pathname + search + hash (not a full external URL). */
fun parseLocation(input: String): RouteLocation {
    require(input.startsWith("/") && !input.startsWith("//")) {
        "Expected a local URL beginning with one slash"
    }
    val h = input.indexOf('#')
    val base = if (h == -1) input else input.substring(0, h)
    val fragment = if (h == -1) null else decodeComponent(input.substring(h + 1))
    val q = base.indexOf('?')
    val rawPath = if (q == -1) base else base.substring(0, q)
    val queryString = if (q == -1) "" else base.substring(q + 1)
    val trimmed = rawPath.trim('/')
    val path = if (trimmed.isEmpty()) emptyList() else trimmed.split('/')
    require(path.none { it.isEmpty() }) { "Empty internal path segment" }
    val parameters = if (queryString.isEmpty()) emptyList() else queryString.split('&').map {
        val k = it.substringBefore('=').replace("+", " ")
        val v = it.substringAfter('=', "").replace("+", " ")
        Parameter(decodeComponent(k), decodeComponent(v))
    }
    return RouteLocation(path.map(::decodeComponent), parameters, fragment)
}

internal fun encodeComponent(value: String): String = buildString {
    val digits = "0123456789ABCDEF"
    for (byte in value.encodeToByteArray()) {
        val n = byte.toInt() and 255
        if (n in 65..90 || n in 97..122 || n in 48..57 || n in listOf(45, 46, 95, 126)) {
            append(n.toChar())
        } else {
            append('%')
            append(digits[n ushr 4])
            append(digits[n and 15])
        }
    }
}

private fun decodeComponent(raw: String): String = buildString {
    var i = 0
    while (i < raw.length) {
        if (raw[i] != '%') {
            append(raw[i++])
            continue
        }
        val bytes = mutableListOf<Byte>()
        while (i < raw.length && raw[i] == '%') {
            require(i + 2 < raw.length) { "Incomplete percent escape" }
            val high = raw[i + 1].digitToIntOrNull(16)
            val low = raw[i + 2].digitToIntOrNull(16)
            require(high != null && low != null) { "Invalid percent escape" }
            bytes += ((high shl 4) or low).toByte()
            i += 3
        }
        append(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
    }
}
