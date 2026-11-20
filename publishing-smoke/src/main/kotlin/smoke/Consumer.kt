package smoke

import org.evoleq.mathcat.actions.Action

// Compiled without MathCat source/project dependencies, only published JARs.
fun actionName(action: Action<String, Int, String>): String = action.name


// Compile-time checks for new artifacts consumed independently from Maven Local.
fun resultName(result: org.evoleq.mathcat.result.Result<String>): String =
    result.toString()
fun declaredApiSize(api: org.evoleq.mathcat.api.Api): Int = api.size
fun contextualValue(value: org.evoleq.mathcat.context.Contextual<String>): String =
    value.data
fun serializationLookup(registry: org.evoleq.mathcat.serialization.Serializers): String =
    registry.toString()

// Plugin manifest API is available independently of MathCat sources.
fun pluginId(): String = org.evoleq.mathcat.plugins.PluginId("banking").value

// Independent publication of the optional host capability adapter.
fun routeName(): String = org.evoleq.mathcat.plugins.actions.PluginRoute("shares.home", "/app/shares").key
