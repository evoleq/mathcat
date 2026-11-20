package org.evoleq.mathcat.routing

import org.evoleq.mathcat.types.Sum2

typealias RoutingResult<A> = Sum2<RouteFailure, A>

/** A successfully matched route. Parameter names and values stay distinct. */
data class RouteMatch<C>(
    val name: String?,
    val template: String,
    val component: C,
    val parameters: Map<String, String>,
    val location: RouteLocation,
    private val guards: List<() -> Boolean>
) {
    fun parameter(name: String): String? = parameters[name]
    fun queryParameter(name: String): String? = location.query[name]?.firstOrNull()

    /** Explicit check: matching itself NEVER runs guards or navigates. */
    fun isGranted(): Boolean = guards.all { it() }
}

/**
 * A compiled immutable route set with matching and reverse URL generation.
 * Building is independent of Compose, browser state and HTTP.
 */
class CompiledRoutes<C> internal constructor(
    private val ordered: List<RouteDefinition<C>>,
    private val byName: Map<String, RouteDefinition<C>>
) {
    fun match(url: String): RoutingResult<RouteMatch<C>> {
        val location = try {
            parseLocation(url)
        } catch (failure: Exception) {
            return Sum2.Left(RouteFailure.MalformedUrl(failure.message ?: "Invalid URL"))
        }
        for (candidate in ordered) {
            if (candidate.segments.size != location.segments.size) continue
            val values = mutableMapOf<String, String>()
            val matches = candidate.segments.zip(location.segments).all { (expected, actual) ->
                when (expected) {
                    is RouteSegment.Static -> expected.value == actual
                    is RouteSegment.Variable -> {
                        if (actual.isEmpty()) false
                        else {
                            values[expected.name] = actual
                            true
                        }
                    }
                }
            }
            if (matches) {
                return Sum2.Right(RouteMatch(
                    candidate.name, candidate.segments.template(), candidate.component,
                    values, location, candidate.guards
                ))
            }
        }
        return Sum2.Left(RouteFailure.NotFound(url))
    }

    /**
     * Legacy access{} runs NOW, not during declaration or match.
     * The application automaton decides how to interpret AccessDenied.
     */
    fun resolve(url: String): RoutingResult<RouteMatch<C>> =
        when (val result = match(url)) {
            is Sum2.Left -> result
            is Sum2.Right -> {
                if (result.value.isGranted()) result
                else Sum2.Left(RouteFailure.AccessDenied(result.value.name ?: result.value.template))
            }
        }

    /** Build a browser-usable speaking URL for a named route. */
    fun urlFor(
        name: String,
        parameters: Map<String, String> = emptyMap(),
        query: List<Parameter> = emptyList(),
        fragment: String? = null
    ): RoutingResult<String> {
        val route = byName[name] ?: return Sum2.Left(RouteFailure.MissingName(name))
        val declared = route.segments.filterIsInstance<RouteSegment.Variable>().map { it.name }.toSet()
        (declared - parameters.keys).firstOrNull()?.let {
            return Sum2.Left(RouteFailure.MissingParameter(it))
        }
        (parameters.keys - declared).firstOrNull()?.let {
            return Sum2.Left(RouteFailure.UnexpectedParameter(it))
        }
        val path = route.segments.map {
            when (it) {
                is RouteSegment.Static -> it.value
                is RouteSegment.Variable -> parameters.getValue(it.name)
            }
        }
        if (path.any { it.isEmpty() }) {
            return Sum2.Left(RouteFailure.MalformedUrl("Empty route parameter value"))
        }
        return Sum2.Right(RouteLocation(path, query, fragment).toUrl())
    }

    val names: Set<String> get() = byName.keys
}

/**
 * Plain route fragments form a monoid under concatenation. Compile checks
 * that a composed set is unambiguous. No higher-kinded machinery required.
 */
fun <C> EmptyRoutes(): Routes<C> = Routes(emptyList())

operator fun <C> Routes<C>.times(other: Routes<C>): Routes<C> =
    Routes(definitions + other.definitions)

/**
 * Checks named routes, duplicated parameter names and overlapping patterns.
 * A more specific static route may shadow a variable route; incomparable
 * overlapping patterns are rejected rather than ordered arbitrarily.
 */
fun <C> Routes<C>.compile(): RoutingResult<CompiledRoutes<C>> {
    val byName = mutableMapOf<String, RouteDefinition<C>>()
    for (route in definitions) {
        val parameterNames = route.segments.filterIsInstance<RouteSegment.Variable>().map { it.name }
        parameterNames.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys.firstOrNull()?.let {
                return Sum2.Left(RouteFailure.DuplicateParameter(it))
            }
        route.name?.let { name ->
            if (byName.put(name, route) != null) {
                return Sum2.Left(RouteFailure.DuplicateName(name))
            }
        }
    }

    for (i in definitions.indices) {
        for (j in i + 1 until definitions.size) {
            val a = definitions[i].segments
            val b = definitions[j].segments
            if (overlaps(a, b) && !moreSpecific(a, b) && !moreSpecific(b, a)) {
                return Sum2.Left(RouteFailure.AmbiguousPattern(a.template(), b.template()))
            }
        }
    }

    // All overlapping routes are now strictly ordered by number of statics.
    val ordered = definitions.sortedByDescending { route ->
        route.segments.count { it is RouteSegment.Static }
    }
    return Sum2.Right(CompiledRoutes(ordered, byName))
}

private fun overlaps(a: List<RouteSegment>, b: List<RouteSegment>): Boolean =
    a.size == b.size && a.zip(b).all { (x, y) ->
        !(x is RouteSegment.Static && y is RouteSegment.Static && x.value != y.value)
    }

/** More statics in every constrained position, at least one strictly more. */
private fun moreSpecific(a: List<RouteSegment>, b: List<RouteSegment>): Boolean =
    a.size == b.size && a.zip(b).all { (x, y) ->
        y !is RouteSegment.Static || x is RouteSegment.Static && x.value == y.value
    } && a.zip(b).any { (x, y) -> x is RouteSegment.Static && y is RouteSegment.Variable }

private fun List<RouteSegment>.template(): String =
    "/" + joinToString("/") {
        when (it) {
            is RouteSegment.Static -> it.value
            is RouteSegment.Variable -> ":${it.name}"
        }
    }
