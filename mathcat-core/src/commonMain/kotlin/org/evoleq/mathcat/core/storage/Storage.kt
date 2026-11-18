package org.evoleq.mathcat.core.storage

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.reader.emit
import org.evoleq.mathcat.core.writer.Writer

/**
 * Synchronous read/write boundary for a value of type [S].
 *
 * The read side is a first-class Source<S> = Reader<Unit, S>.
 *
 * Storage itself does not promise persistence, atomicity, thread safety,
 * snapshot isolation or suspension. Those must be supplied explicitly by
 * backends with stronger guarantees. IO requiring suspension needs a
 * separate suspend-aware abstraction; it should not block here.
 */
interface Storage<S> {
    val source: Source<S>
    fun write(value: S)

    /** Convenience operation: evaluate the live Source now. */
    fun read(): S = source.emit()
}

/**
 * Construct Storage from an existing Source and a write function.
 *
 * This adapts external storage implementations without imposing inheritance.
 * The factory is capitalized in the existing MathCat constructor vocabulary.
 */
@Suppress("FunctionName")
fun <S> Storage(source: Source<S>, write: (S) -> Unit): Storage<S> {
    val sink = write
    return object : Storage<S> {
        override val source: Source<S> = source
        override fun write(value: S) = sink(value)
    }
}

/** Convenience factory for existing callback-style backends. */
@Suppress("FunctionName")
fun <S> Storage(read: () -> S, write: (S) -> Unit): Storage<S> =
    Storage(source = Source(read), write = write)

/** Read from Storage through a Reader, retaining laziness and live values. */
operator fun <S, A> Storage<S>.times(reader: Reader<S, A>): Source<A> =
    Source { reader(read()) }

/**
 * Focus a Storage through a lawful Lens.
 *
 * Reading remains live. Writing reads the whole current state once, then
 * reconstructs it through the Lens. This read-modify-write is NOT atomic:
 * concurrent external writes may be lost.
 */
operator fun <S, A> Storage<S>.times(lens: Lens<S, A>): Storage<A> =
    Storage(
        source = Source { lens.get(read()) },
        write = { value ->
            val current = read()
            write(lens.set(value)(current))
        }
    )

/**
 * Focus a Storage through a Selection without claiming Lens laws.
 *
 * Writing a value just read may modify the underlying state, because an
 * arbitrary Selection can fail the Get-Put law.
 */
operator fun <S, A> Storage<S>.times(selection: Selection<S, A>): Storage<A> =
    Storage(
        source = Source { selection.get(read()) },
        write = { value ->
            val current = read()
            write(selection.set(value)(current))
        }
    )

/** Use a pure Writer as a command handler against current Storage contents. */
fun <S, A> Storage<S>.writeWith(writer: Writer<S, A>, command: A) {
    val current = read()
    write(writer(command)(current))
}
