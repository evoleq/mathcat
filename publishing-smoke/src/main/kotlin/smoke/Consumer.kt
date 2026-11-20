package smoke

import evoleq.toolkit.actions.Action

// Compiled without Toolkit source/project dependencies, only published JARs.
fun actionName(action: Action<String, Int, String>): String = action.name


// Compile-time checks for new artifacts consumed independently from Maven Local.
fun resultName(result: evoleq.toolkit.result.Result<String>): String =
    result.toString()
fun declaredApiSize(api: evoleq.toolkit.api.Api): Int = api.size
fun contextualValue(value: evoleq.toolkit.context.Contextual<String>): String =
    value.data
fun serializationLookup(registry: evoleq.toolkit.serialization.Serializers): String =
    registry.toString()

// Plugin manifest API is available independently of Toolkit sources.
fun pluginId(): String = evoleq.toolkit.plugins.PluginId("banking").value

// Independent publication of the optional host capability adapter.
fun routeName(): String = evoleq.toolkit.plugins.actions.PluginRoute("shares.home", "/app/shares").key
