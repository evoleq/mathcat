package smoke

import org.evoleq.mathcat.actions.Action

// Compiled without MathCat source/project dependencies, only published JARs.
fun actionName(action: Action<String, Int, String>): String = action.name
