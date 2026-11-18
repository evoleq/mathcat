package org.evoleq.mathcat.core.writer

import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherTest {
    @Test
    fun dispatcherIsWriterOverUnit() {
        val commands = mutableListOf<String>()
        val dispatcher: Dispatcher<String> =
            Writer { command -> { _: Unit -> commands.add(command); Unit } }

        dispatcher.dispatch("one")
        dispatcher.dispatch("two")
        assertEquals(listOf("one", "two"), commands)
    }
}
