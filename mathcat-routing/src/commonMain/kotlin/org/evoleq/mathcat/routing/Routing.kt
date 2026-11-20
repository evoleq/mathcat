package org.evoleq.mathcat.routing

import org.evoleq.mathcat.types.Sum2

/** Immutable, transport- and UI-independent descriptions of browser routes. */
sealed interface Segment {
    data class Static(val text: String) : Segment
    data class Parameter(val name: String) : Segment
}

data class RouteDefinition(
    val name: String,
    val segments: List<Segment>,
    val requires: Set<String> = emptySet(),
    val layouts: List<String> = emptyList()
)

data class Routes(val definitions: List<RouteDefinition>)

data class RouteMatch(
    val name: String,
    val parameters: Map<String, String>,
    val query: Map<String, List<String>>,
    val requires: Set<String>,
    val layouts: List<String>
)

sealed interface RouteError {
    data class InvalidPattern(val pattern: String) : RouteError
    data class InvalidUrl(val url: String) : RouteError
    data class DuplicateName(val name: String) : RouteError
    data class Ambiguous(val first: String, val second: String) : RouteError
    data class UnknownName(val name: String) : RouteError
    data class MissingParameter(val name: String) : RouteError
    data class UnexpectedParameter(val name: String) : RouteError
    data class NotFound(val url: String) : RouteError
}

@DslMarker annotation class RoutingDsl

/**
 * The familiar nested Solyton route DSL, as a pure description.
 * Component, wrap and browser History effects belong to adapters.
 */
@RoutingDsl
class RoutesConfiguration internal constructor(
    private val path: List<Segment> = emptyList()
) {
    private var routeName: String? = null
    private val grants = mutableSetOf<String>()
    private val layouts = mutableListOf<String>()
    private val children = mutableListOf<RoutesConfiguration>()
    private val fragments = mutableListOf<Routes>()

    fun requires(vararg permissions: String) {
        require(permissions.all { it.isNotBlank() }) { "Permissions must be nonempty" }
        grants.addAll(permissions)
    }

    /** Layout names are pure metadata; a renderer owns their Composables. */
    fun layout(name: String) {
        require(name.isNotBlank()) { "Layout name must not be empty" }
        layouts.add(name)
    }

    fun name(name: String) {
        require(name.isNotBlank()) { "Route name must not be empty" }
        require(routeName == null) { "A route may have only one name" }
        routeName = name
    }

    fun route(pattern: String, configure: RoutesConfiguration.() -> Unit) {
        val child = RoutesConfiguration(path + parsePattern(pattern))
        child.configure()
        children.add(child)
    }

    /** Module fragments can be composed beneath the current prefix. */
    fun include(fragment: Routes) {
        fragments.add(fragment)
    }

    /** Configuration-time condition; never a substitute for an auth guard. */
    fun nonProdRoute(pattern: String, environment: String, configure: RoutesConfiguration.() -> Unit) {
        if (environment.lowercase() !in setOf("p", "prod", "production")) route(pattern, configure)
    }

    private fun collect(grantsFromParent: Set<String>, layoutsFromParent: List<String>): List<RouteDefinition> {
        val allGrants = grantsFromParent + grants
        val allLayouts = layoutsFromParent + layouts
        val own = routeName?.let { listOf(RouteDefinition(it, path, allGrants, allLayouts)) }
            ?: emptyList()
        val included = fragments.flatMap { fragment ->
            fragment.definitions.map { definition ->
                definition.copy(
                    segments = path + definition.segments,
                    requires = allGrants + definition.requires,
                    layouts = allLayouts + definition.layouts
                )
            }
        }
        return own + included + children.flatMap { it.collect(allGrants, allLayouts) }
    }

    internal fun done(): Routes = Routes(collect(emptySet(), emptyList()))
}

/** Strictly validate patterns; support both {name} and legacy :name. */
private fun parsePattern(pattern: String): List<Segment> {
    val parts = pattern.trim('/').split('/').filter { it.isNotEmpty() }
    return parts.map { part ->
        val parameter = when {
            part.startsWith(":") -> part.drop(1)
            part.startsWith("{") && part.endsWith("}") -> part.drop(1).dropLast(1)
            else -> null
        }
        if (parameter != null) {
            require(parameter.isNotEmpty() && parameter.first().isLetter() &&
                parameter.all { it.isLetterOrDigit() || it == '_' }) {
                "Invalid route parameter: " + part
            }
            Segment.Parameter(parameter)
        } else {
            require(part.isNotBlank() && '{' !in part && '}' !in part && ':' !in part &&
                '?' !in part && '#' !in part && '%' !in part && part != "." && part != "..") {
                "Invalid static route segment: " + part
            }
            Segment.Static(part)
        }
    }
}

@Suppress("FunctionName")
fun Routes(configure: RoutesConfiguration.() -> Unit): Routes =
    RoutesConfiguration().apply(configure).done()

/** Solyton-friendly spelling retained alongside capitalized constructor. */
fun routes(configure: RoutesConfiguration.() -> Unit): Routes = Routes(configure)

class CompiledRoutes internal constructor(val definitions: List<RouteDefinition>) {
    /** Pure and deterministic: a static segment always outranks a parameter. */
    fun match(url: String): Sum2<RouteError, RouteMatch> {
        val parsed = parseUrl(url) ?: return Sum2.Left(RouteError.InvalidUrl(url))
        val (parts, query) = parsed
        val matches = definitions.mapNotNull { def ->
            if (def.segments.size != parts.size) return@mapNotNull null
            val vars = mutableMapOf<String, String>()
            if (!def.segments.zip(parts).all { (segment, value) ->
                    when (segment) {
                        is Segment.Static -> segment.text == value
                        is Segment.Parameter -> {
                            val previous = vars.put(segment.name, value)
                            previous == null || previous == value
                        }
                    }
                }) return@mapNotNull null
            val specificity = def.segments.joinToString("") { if (it is Segment.Static) "1" else "0" }
            Triple(def, vars.toMap(), specificity)
        }
        val selected = matches.maxByOrNull { it.third }
            ?: return Sum2.Left(RouteError.NotFound(url))
        return Sum2.Right(RouteMatch(
            selected.first.name, selected.second, query,
            selected.first.requires, selected.first.layouts
        ))
    }

    /** Build deep links from a named route. No implicit global navigation. */
    fun url(name: String, parameters: Map<String, String> = emptyMap(),
            query: List<Pair<String, String>> = emptyList()): Sum2<RouteError, String> {
        val def = definitions.firstOrNull { it.name == name }
            ?: return Sum2.Left(RouteError.UnknownName(name))
        val used = mutableSetOf<String>()
        val parts = mutableListOf<String>()
        for (segment in def.segments) {
            when (segment) {
                is Segment.Static -> parts.add(encode(segment.text))
                is Segment.Parameter -> {
                    val value = parameters[segment.name]
                        ?: return Sum2.Left(RouteError.MissingParameter(segment.name))
                    if (value.isEmpty() || value == "." || value == ".." || '/' in value || '\u0000' in value)
                        return Sum2.Left(RouteError.InvalidUrl(value))
                    used.add(segment.name)
                    parts.add(encode(value))
                }
            }
        }
        val unknown = parameters.keys.firstOrNull { it !in used }
        if (unknown != null) return Sum2.Left(RouteError.UnexpectedParameter(unknown))
        val path = "/" + parts.joinToString("/")
        val suffix = if (query.isEmpty()) "" else "?" + query.joinToString("&") {
            encode(it.first) + "=" + encode(it.second)
        }
        return Sum2.Right(path + suffix)
    }
}

/** Check duplicate names and overlapping equal-specificity route patterns. */
@Suppress("FunctionName")
fun CompileRoutes(routes: Routes): Sum2<List<RouteError>, CompiledRoutes> {
    val errors = mutableListOf<RouteError>()
    val defs = routes.definitions
    for (i in defs.indices) for (j in i + 1 until defs.size) {
        val a = defs[i]
        val b = defs[j]
        if (a.name == b.name) errors.add(RouteError.DuplicateName(a.name))
        if (a.segments.size != b.segments.size) continue
        val samePriority = a.segments.zip(b.segments).all { (x, y) ->
            (x is Segment.Static) == (y is Segment.Static)
        }
        val overlaps = a.segments.zip(b.segments).all { (x, y) ->
            x !is Segment.Static || y !is Segment.Static || x.text == y.text
        }
        if (samePriority && overlaps) errors.add(RouteError.Ambiguous(a.name, b.name))
    }
    return if (errors.isEmpty()) Sum2.Right(CompiledRoutes(defs)) else Sum2.Left(errors)
}

private fun encode(raw: String): String {
    val digits = "0123456789ABCDEF"
    val safe = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    return buildString {
        for (byte in raw.encodeToByteArray()) {
            val number = byte.toInt() and 255
            val character = number.toChar()
            if (character in safe) append(character) else {
                append('%')
                append(digits[number shr 4])
                append(digits[number and 15])
            }
        }
    }
}

private fun decode(raw: String): String? {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < raw.length) {
        if (raw[index] == '%') {
            if (index + 2 >= raw.length) return null
            val hi = raw[index + 1].digitToIntOrNull(16) ?: return null
            val lo = raw[index + 2].digitToIntOrNull(16) ?: return null
            bytes.add(((hi shl 4) or lo).toByte())
            index += 3
        } else {
            val start = index
            while (index < raw.length && raw[index] != '%') index++
            bytes.addAll(raw.substring(start, index).encodeToByteArray().toList())
        }
    }
    return try { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) }
    catch (_: IllegalArgumentException) { null }
}

private fun parseUrl(url: String): Pair<List<String>, Map<String, List<String>>>? {
    if (!url.startsWith('/') || '\u0000' in url) return null
    val withoutFragment = url.substringBefore('#')
    val pathPart = withoutFragment.substringBefore('?')
    val parts = mutableListOf<String>()
    for (raw in pathPart.split('/').filter { it.isNotEmpty() }) {
        val value = decode(raw) ?: return null
        if (value == "." || value == ".." || '/' in value || '\u0000' in value) return null
        parts.add(value)
    }
    val query = linkedMapOf<String, MutableList<String>>()
    if ('?' in withoutFragment) {
        for (entry in withoutFragment.substringAfter('?').split('&').filter { it.isNotEmpty() }) {
            val key = decode(entry.substringBefore('=')) ?: return null
            val value = decode(entry.substringAfter('=', "")) ?: return null
            query.getOrPut(key) { mutableListOf() }.add(value)
        }
    }
    return parts to query.mapValues { it.value.toList() }
}
