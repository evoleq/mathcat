package evoleq.toolkit.consumer

import evoleq.toolkit.core.state.KlState
import evoleq.toolkit.core.state.Return as StateReturn
import evoleq.toolkit.core.state.Update
import evoleq.toolkit.core.state.times
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Public API smoke test in an independent package.
 *
 * Import aliases let callers distinguish constructors named Return across
 * future Monad packages, without forcing verbose companion qualifiers.
 * There is no second Monad implementation in the library yet.
 */
class ConstructorImportsTest {
    @Test
    fun standaloneReturnCanBeAliasedAndComposedWithStandaloneUpdate() {
        val change: KlState<Int, String, String> = Update { it + 2 }
        val computation = StateReturn<Int, String>("request") * change

        assertEquals("request" to 7, computation(5))
    }
}
