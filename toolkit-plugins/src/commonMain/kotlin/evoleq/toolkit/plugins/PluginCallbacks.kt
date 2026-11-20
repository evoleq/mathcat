package evoleq.toolkit.plugins

/**
 * A host-owned, minimal command boundary for dynamically loaded plugins.
 * Identifiers arrive over the plain JavaScript module ABI, therefore they
 * must be resolved through a HOST allowlist. This interface conveys no
 * Application state and grants no implicit authorization.
 *
 * Returning false means denied/unknown; exceptions from actual host effects
 * are NOT silently converted into success. Acknowledgement means dispatch
 * accepted the Action, not that remote processing finished.
 */
interface PluginCallbacks {
    suspend fun dispatchAction(plugin: PluginId, actionKey: String): Boolean
    suspend fun navigate(plugin: PluginId, destinationKey: String): Boolean
}
