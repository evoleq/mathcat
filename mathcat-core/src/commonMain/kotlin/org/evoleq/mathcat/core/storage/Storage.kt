package org.evoleq.mathcat.core.storage

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.writer.Writer

/**
 * An explicit effect boundary: a readable and writable location containing S.
 *
 * This is a handle onto external mutable state, not a pure value and not
 * inherently atomic, thread-safe or suspendable. No hidden internal backing
 * store, synchronization or lifecycle semantics are implied.
 */
data class Storage<S>(
    val read: () -> S,
    val write: (S) -> Unit
)

/** A Storage can be read lazily through any Reader; the result is a Source. */
operator fun <S, A> Storage<S>.times(reader: Reader<S, A>): Source<A> =
    Source { reader(read()) }

/**
 * Focus a Storage through a lawful Lens.
 *
 * Each read observes the current backing state. Each write reads the current
 * state once and reconstructs it through the Lens, preserving other fields.
 *
 * This read-modify-write operation is not atomic; concurrent writes may be lost.
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
 * Focus a Storage through a composable Selection.
 *
 * Unlike Lens, Selection does not promise read/write laws: writing back the
 * value just read may change the underlying state. This is intentional.
 */
operator fun <S, A> Storage<S>.times(selection: Selection<S, A>): Storage<A> =
    Storage(
        read = { selection.get(read()) },
        write = { value ->
            val current = read()
            write(selection.set(value)(current))
        }
    )

/** Apply a command to storage using a pure Writer on the stored value. */
fun <S, A> Storage<S>.writeWith(writer: Writer<S, A>, command: A) {
    val current = read()
    write(writer(command)(current))
}

/** Read storage as a Source, preserving deferred reads. */
fun <S> Storage<S>.source(): Source<S> = Source(read)
