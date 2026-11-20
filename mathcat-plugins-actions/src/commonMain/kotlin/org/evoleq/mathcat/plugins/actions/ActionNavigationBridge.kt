package org.evoleq.mathcat.plugins.actions

import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.actions.ActionDispatcher
import org.evoleq.mathcat.actions.ActionEnvelope
import org.evoleq.mathcat.plugins.PluginCallbacks
import org.evoleq.mathcat.plugins.PluginId

/**
 * Type-safe host binding. The plugin sends only an opaque action KEY;
 * it does not receive an Action<S,I,O>, a Storage<S> or an HTTP transport.
 *
 * When invoked, the HOST dispatches the captured, statically typed Action.
 * The action's Reader remains owned by the host, as in the FE middleware.
 */
class BoundAction<S> internal constructor(
    val key: String,
    private val perform: suspend (ActionDispatcher<S>) -> Unit
) {
    init { require(key.isNotBlank()) }
    suspend fun dispatch(on: ActionDispatcher<S>) = perform(on)
}

@Suppress("FunctionName")
fun <S, I, O> BindAction(
    key: String,
    action: Action<S, I, O>
): BoundAction<S> = BoundAction(key) { dispatcher -> dispatcher.dispatch(action) }

@Suppress("FunctionName")
fun <S, I, O> BindEnvelope(
    key: String,
    envelope: ActionEnvelope<S, I, O>
): BoundAction<S> =
    BoundAction(key) { dispatcher -> dispatcher.dispatchEnvelope(envelope) }

/**
 * A navigation key resolves to a fixed, host-owned internal path. The
 * remote plugin cannot supply an arbitrary URL or directly access history.
 */
data class PluginRoute(val key: String, val path: String) {
    init {
        require(key.isNotBlank())
        require(path.startsWith("/") && !path.startsWith("//")) {
            "Plugin routes must use an absolute internal path, not an external URL"
        }
    }
}

fun interface PluginNavigator {
    suspend fun navigate(path: String)
}

/**
 * Fine-grained per-plugin allowlist configured by the HOST, not read from
 * an externally deployed plugin manifest. It is a capability projection,
 * NOT the application's authentication / authorization decision.
 */
data class PluginGrant(
    val actions: Set<String> = emptySet(),
    val routes: Set<String> = emptySet()
)

/**
 * Kotlin MPP host adapter for JS microfrontends (or other plugin loaders).
 * The wire interface is tiny and string-keyed, while the host's Action
 * bindings stay statically typed. Unknown/unauthorized keys fail closed.
 */
class ActionNavigationBridge<S>(
    private val dispatcher: ActionDispatcher<S>,
    private val navigator: PluginNavigator,
    actions: List<BoundAction<S>>,
    routes: List<PluginRoute>,
    private val grants: Map<PluginId, PluginGrant>
) : PluginCallbacks {
    private val boundActions = actions.associateBy { it.key }
    private val boundRoutes = routes.associateBy { it.key }

    init {
        require(boundActions.size == actions.size) { "Duplicate plugin Action keys" }
        require(boundRoutes.size == routes.size) { "Duplicate plugin route keys" }
    }

    override suspend fun dispatchAction(plugin: PluginId, actionKey: String): Boolean {
        if (actionKey !in (grants[plugin]?.actions ?: emptySet())) return false
        val action = boundActions[actionKey] ?: return false
        action.dispatch(dispatcher)
        return true
    }

    override suspend fun navigate(plugin: PluginId, destinationKey: String): Boolean {
        if (destinationKey !in (grants[plugin]?.routes ?: emptySet())) return false
        val route = boundRoutes[destinationKey] ?: return false
        navigator.navigate(route.path)
        return true
    }
}
