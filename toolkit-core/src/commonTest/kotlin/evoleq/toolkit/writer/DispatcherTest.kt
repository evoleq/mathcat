package evoleq.toolkit.writer

import evoleq.toolkit.core.writer.Dispatcher
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.core.writer.dispatch
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherTest {
    @Test
    fun dispatcherIsAWriterOverUnitAndDispatchesInOrder() {
        val commands = mutableListOf<String>()
        val dispatcher: Dispatcher<String> = Writer { command ->
            { _: Unit -> commands.add(command); Unit }
        }

        assertEquals(emptyList(), commands)
        dispatcher.dispatch("first")
        dispatcher.dispatch("second")
        assertEquals(listOf("first", "second"), commands)
    }
}
