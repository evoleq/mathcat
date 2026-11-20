package central.smoke

import evoleq.toolkit.actions.Action
import evoleq.toolkit.api.Api
import evoleq.toolkit.context.Contextual
import evoleq.toolkit.evolution.Flow

// No ToolKit sources or project() dependencies: compile the published API.
fun actionName(action: Action<String, Int, String>): String = action.name
fun apiSize(api: Api): Int = api.size
fun contextualValue(value: Contextual<String>): String = value.data
fun flowType(flow: Flow<*, *, *>): String = flow.toString()
