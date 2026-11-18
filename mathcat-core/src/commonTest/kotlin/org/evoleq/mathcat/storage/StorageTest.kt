package org.evoleq.mathcat.core.storage

import org.evoleq.mathcat.core.collections.FilterBy
import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.emit
import org.evoleq.mathcat.core.writer.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StorageTest {
    data class Model(val name: String, val values: List<Int>)

    private val nameLens = Lens<Model, String>(
        get = Reader { it.name },
        set = Writer { name -> { original -> original.copy(name = name) } }
    )

    private val valuesLens = Lens<Model, List<Int>>(
        get = Reader { it.values },
        set = Writer { values -> { original -> original.copy(values = values) } }
    )

    private fun fixture(): Pair<Storage<Model>, () -> Model> {
        val memory = Memory(Model("before", listOf(1, 2, 3)))
        return memory to { memory.read() }
    }

    @Test
    fun memoryIsAnImplementationOfStorageWithLiveSource() {
        val memory: Storage<Int> = Memory(1)
        val reading = memory.source

        assertEquals(1, reading.emit())
        memory.write(2)
        assertEquals(2, reading.emit())
        assertEquals(2, memory.read())
    }

    @Test
    fun sourceAndCallbackCanBackAStorageWithoutMemory() {
        var raw = 5
        val storage = Storage(
            source = org.evoleq.mathcat.core.reader.Source { raw },
            write = { raw = it }
        )
        val source = storage.source

        assertEquals(5, source.emit())
        storage.write(9)
        assertEquals(9, source.emit())
    }

    @Test
    fun storageLensIsWritableAndPreservesUnfocusedFields() {
        val (storage, snapshot) = fixture()
        val focused = storage * nameLens

        assertEquals("before", focused.read())
        focused.write("after")
        assertEquals(Model("after", listOf(1, 2, 3)), snapshot())
        assertEquals("after", focused.read())
    }

    @Test
    fun storageReaderProducesLazySourceOfCurrentValue() {
        val (storage, _) = fixture()
        val selected = storage * Reader<Model, String> { it.name }
        assertEquals("before", selected.emit())

        (storage * nameLens).write("changed")
        assertEquals("changed", selected.emit())
        assertEquals("changed", storage.source.emit().name)
    }

    @Test
    fun storageSelectionRemainsUsableWithoutLensLawClaims() {
        val (storage, snapshot) = fixture()
        val filtered = storage * valuesLens * FilterBy<Int> { it % 2 == 0 }
        assertEquals(listOf(2), filtered.read())
        filtered.write(filtered.read())
        assertEquals(listOf(1, 3, 2), snapshot().values)
        assertNotEquals(listOf(1, 2, 3), snapshot().values)
    }

    @Test
    fun writeWithAppliesWriterToCurrentStorageState() {
        val (storage, snapshot) = fixture()
        val writer = Writer<Model, String> { name -> { model -> model.copy(name = name) } }
        storage.writeWith(writer, "new")

        assertEquals("new", snapshot().name)
    }
}
