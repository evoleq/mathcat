package org.evoleq.mathcat.writer

import org.evoleq.mathcat.core.writer.Dispatcher
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.core.writer.dispatch
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
