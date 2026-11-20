package evoleq.toolkit.storage

import evoleq.toolkit.core.optics.Lens
import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.reader.emit
import evoleq.toolkit.core.storage.Dispatch
import evoleq.toolkit.core.storage.Memory
import evoleq.toolkit.core.storage.Read
import evoleq.toolkit.core.storage.Storage
import evoleq.toolkit.core.storage.times
import evoleq.toolkit.core.writer.Dispatcher
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.core.writer.contraMap
import evoleq.toolkit.core.writer.dispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageCommandsTest {
    data class Part(val title: String, val count: Int)
    data class Whole(val part: Part, val revision: Int)
    data class Response(val title: String)

    private val partLens = Lens(
        get = Reader<Whole, Part> { it.part },
        set = Writer<Whole, Part> { part -> { state -> state.copy(part = part) } }
    )

    private val rename: Writer<Part, String> = Writer { title ->
        { part -> part.copy(title = title) }
    }

    @Test
    fun storageWriterIsDeferredAndReadsLatestStateOnEachDispatch() {
        var state = Whole(Part("before", 2), revision = 10)
        var reads = 0
        var writes = 0
        val storage: Storage<Whole> = Storage(
            read = { reads++; state },
            write = { writes++; state = it }
        )
        val dispatcher: Dispatcher<String> = storage * partLens * rename

        assertEquals(0, reads)
        assertEquals(0, writes)

        state = Whole(Part("interleaved", 7), revision = 11)
        dispatcher.dispatch("updated")
        assertEquals(Whole(Part("updated", 7), 11), state)
        // (Storage * lens) * writer reads the root state once for the focused
        // Writer and again when the focused Storage reconstructs the whole.
        // This is deliberately non-atomic; it is not a single-read transaction.
        assertEquals(2, reads)
        assertEquals(1, writes)

        state = Whole(Part("another", 9), revision = 12)
        dispatcher.dispatch("latest")
        assertEquals(Whole(Part("latest", 9), 12), state)
        assertEquals(4, reads)
        assertEquals(2, writes)
    }

    @Test
    fun storageDispatcherSupportsWriterContraMap() {
        val memory = Memory(Whole(Part("old", 1), 4))
        val dispatchResponse: Dispatcher<Response> =
            (memory * partLens * rename).contraMap { response: Response -> response.title }

        dispatchResponse.dispatch(Response("renamed"))
        assertEquals(Whole(Part("renamed", 1), 4), memory.read())
    }

    @Test
    fun readAndDispatchUseExistingStorageAndDeferSideEffects() {
        val storage = Memory(Whole(Part("start", 3), 1))
        val source = Read(storage)
        val dispatcher = Dispatch(storage)

        assertEquals(Whole(Part("start", 3), 1), source.emit())
        storage.write(Whole(Part("external", 4), 2))
        assertEquals(Whole(Part("external", 4), 2), source.emit())

        dispatcher.dispatch(Whole(Part("replacement", 5), 3))
        assertEquals(Whole(Part("replacement", 5), 3), storage.read())
    }

    @Test
    fun failedWriterDoesNotInvokeStorageWrite() {
        var writes = 0
        val storage: Storage<Int> = Storage(read = { 4 }, write = { writes++ })
        val writer = Writer<Int, String> { _ ->
            { _: Int -> throw IllegalStateException("writer failed") }
        }
        val dispatcher = storage * writer

        assertFailsWith<IllegalStateException> { dispatcher.dispatch("fail") }
        assertEquals(0, writes)
    }
}
