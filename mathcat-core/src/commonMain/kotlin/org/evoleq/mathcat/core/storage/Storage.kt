package org.evoleq.mathcat.core.storage

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.reader.emit
import org.evoleq.mathcat.core.writer.Writer

/**
 * Synchronous read/write boundary for a value S.
 *
 * A Storage is NOT required to own one backing cell. Its read/write operations
 * may assemble and route updates across several independently managed memories.
 *
 * No persistence, atomicity, thread safety, Compose observation or suspend
 * semantics are implied by this minimal commonMain contract.
 */
interface Storage<S> {
    fun read(): S
    fun write(value: S)
}

/**
 * Adapt existing synchronous callbacks to Storage.
 *
 * The callbacks may work with one Memory, several Memories, or another backend.
 */
@Suppress("FunctionName")
fun <S> Storage(read: () -> S, write: (S) -> Unit): Storage<S> {
    val reader = read
    val writer = write
    return object : Storage<S> {
        override fun read(): S = reader()
        override fun write(value: S) = writer(value)
    }
}

/** Adapt a Source and a write callback to the ordinary read/write contract. */
@Suppress("FunctionName")
fun <S> Storage(source: Source<S>, write: (S) -> Unit): Storage<S> =
    Storage(read = { source.emit() }, write = write)

/**
 * Project the read-only side of a Storage as a live Source = Reader<Unit,S>.
 *
 * source() is a conversion, NOT a required property of Storage. read() and
 * write() remain the primary operations of the interface.
 */
fun <S> Storage<S>.source(): Source<S> = Source { read() }

/** Read through a Reader and retain deferred evaluation. */
operator fun <S, A> Storage<S>.times(reader: Reader<S, A>): Source<A> =
    Source { reader(read()) }

/**
 * Focus through a lawful Lens. Reading remains live; writing reconstructs
 * the whole S from its current value. Read-modify-write is NOT atomic.
 */
operator fun <S, A> Storage<S>.times(lens: Lens<S, A>): Storage<A> =
    Storage(
        read = { lens.get(read()) },
        write = { value ->
            val current = read()
            write(lens.set(value)(current))
        }
    )

/**
 * Focus through a Selection without asserting lens laws.
 * In particular, writing back a selected read may change the underlying S.
 */
operator fun <S, A> Storage<S>.times(selection: Selection<S, A>): Storage<A> =
    Storage(
        read = { selection.get(read()) },
        write = { value ->
            val current = read()
            write(selection.set(value)(current))
        }
    )

/** Execute a Writer command against the current value of a Storage. */
fun <S, A> Storage<S>.writeWith(writer: Writer<S, A>, command: A) {
    val current = read()
    write(writer(command)(current))
}
