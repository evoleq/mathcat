package org.evoleq.mathcat.routing

/** A pure route declaration: no browser, Compose runtime or CoroutineScope. */
@DslMarker
annotation class RoutingDsl

sealed interface RouteSegment {
    data class Static(val value: String) : RouteSegment
    data class Variable(val name: String) : RouteSegment
}

/** The component may be a Compose renderer, a route ID, or any host capability. */
data class RouteDefinition<C>(
    val segments: List<RouteSegment>,
    val component: C,
    val name: String?,
    val guards: List<() -> Boolean>
)

class Routes<C> internal constructor(val definitions: List<RouteDefinition<C>>)

/**
 * Retains Solyton's route / wrap / layout / access / component /
 * nonProdRoute vocabulary. Build is pure except for the legacy
 * explicit nonProdRoute environment predicate; access is deferred.
 */
@RoutingDsl
class RoutesConfiguration<C> internal constructor(
    private val segments: List<RouteSegment> = emptyList()
) {
    private val children = mutableListOf<RoutesConfiguration<C>>()
    private val guards = mutableListOf<() -> Boolean>()
    private val layouts = mutableListOf<LayoutRule<C>>()
    private var content: C? = null
    private var routeName: String? = null

    @RoutingDsl
    fun route(path: String, block: RoutesConfiguration<C>.() -> Unit) {
        children += RoutesConfiguration<C>(parseTemplate(path)).apply(block)
    }

    /** Group declarations without changing the URL prefix. */
    @RoutingDsl
    fun wrap(block: RoutesConfiguration<C>.() -> Unit) {
        children += RoutesConfiguration<C>().apply(block)
    }

    @RoutingDsl
    fun nonProdRoute(path: String, env: String, block: RoutesConfiguration<C>.() -> Unit) {
        if (env.trim().lowercase() !in setOf("p", "prod", "production")) {
            route(path, block)
        }
    }

    @RoutingDsl
    fun component(value: C) {
        check(content == null) { "A route may define only one component" }
        content = value
    }

    /**
     * A host-supplied renderer wrapper. "childrenOnly" has the same role
     * as in Solyton, and false includes the declaring route.
     * Nested layouts compose outermost first.
     */
    @RoutingDsl
    fun layout(childrenOnly: Boolean = true, wrapper: (C) -> C) {
        layouts += LayoutRule(childrenOnly, wrapper)
    }

    /** Deferred until explicit authorization; never used as a matcher. */
    @RoutingDsl
    fun access(claim: () -> Boolean) { guards += claim }

    /** Stable route identifier for reverse routing / speaking URLs. */
    @RoutingDsl
    fun name(value: String) {
        require(value.isNotBlank()) { "Route names must not be blank" }
        routeName = value
    }

    internal fun collect(
        prefix: List<RouteSegment> = emptyList(),
        inheritedGuards: List<() -> Boolean> = emptyList(),
        inheritedLayouts: List<(C) -> C> = emptyList()
    ): List<RouteDefinition<C>> {
        val full = prefix + segments
        val allGuards = inheritedGuards + guards
        val ownLayouts = inheritedLayouts + layouts.filterNot { it.childrenOnly }.map { it.wrapper }
        val own = content?.let { value ->
            val wrapped = ownLayouts.asReversed().fold(value) { current, wrapper -> wrapper(current) }
            listOf(RouteDefinition(full, wrapped, routeName, allGuards))
        } ?: emptyList()

        require(content != null || routeName == null) {
            "A named route requires a component"
        }
        val childrenLayouts = inheritedLayouts + layouts.map { it.wrapper }
        return own + children.flatMap { it.collect(full, allGuards, childrenLayouts) }
    }

    private data class LayoutRule<C>(val childrenOnly: Boolean, val wrapper: (C) -> C)
}

fun <C> routing(block: RoutesConfiguration<C>.() -> Unit): Routes<C> =
    Routes(RoutesConfiguration<C>().apply(block).collect())

private fun parseTemplate(raw: String): List<RouteSegment> {
    require('?' !in raw && '#' !in raw) { "Use path templates, not URLs: $raw" }
    val clean = raw.trim('/')
    if (clean.isEmpty()) return emptyList()
    return clean.split('/').map { segment ->
        require(segment.isNotEmpty()) { "Empty internal path segment: $raw" }
        when {
            segment.startsWith(':') -> variable(segment.drop(1))
            segment.startsWith('{') && segment.endsWith('}') ->
                variable(segment.substring(1, segment.lastIndex))
            segment.startsWith('{') || segment.contains('}') ->
                throw IllegalArgumentException("Malformed parameter: $segment")
            else -> RouteSegment.Static(segment)
        }
    }
}

private fun variable(name: String): RouteSegment.Variable {
    require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {
        "Parameter name must be portable: $name"
    }
    return RouteSegment.Variable(name)
}
