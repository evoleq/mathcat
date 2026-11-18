package org.evoleq.mathcat.storage

import org.evoleq.mathcat.core.optics.Lens
import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.reader.Source
import org.evoleq.mathcat.core.writer.Dispatcher
import org.evoleq.mathcat.core.writer.Writer

/**
 * A synchronous read/write boundary for externally held mutable state.
 *
 * read() returns the current value; write(value) replaces it.
 * The callbacks can have effects. Read-transform-write is NOT atomic and does
 * not provide thread safety, transactions, or cancellation semantics.
 *
 * An effectful Storage is not a lawful Lens by mere type equivalence.
 */
data class Storage<S>(
    val read: () -> S,
    val write: (S) -> Unit
)

/**
 * Focus a Storage through a lawful Lens.
 *
 * Reads and writes remain LIVE: on each call the outer Storage is accessed.
 * In particular, the setter reads the latest outer state at write time.
 *
 * This is a read-modify-write sequence, not an atomic update.
 */
operator fun <S, A> Storage<S>.times(lens: Lens<S, A>): Storage<A> =
    Storage(
        read = { lens.get(read()) },
        write = { newFocus ->
            val current = read()
            write(lens.set(newFocus)(current))
        }
    )

/**
 * Focus a Storage through a Selection, retaining its operational semantics.
 *
 * Unlike Lens, Selection does not promise Get-Put / Put-Get / Put-Put;
 * clients must not assume that writing a value preserves other ordering.
 */
operator fun <S, A> Storage<S>.times(selection: Selection<S, A>): Storage<A> =
    Storage(
        read = { selection.get(read()) },
        write = { newFocus ->
            val current = read()
            write(selection.set(newFocus)(current))
        }
    )

/**
 * Storage * Reader -> Source. The Reader is evaluated against the current
 * Storage value each time the Source is emitted; it does not capture a snapshot.
 */
operator fun <S, A> Storage<S>.times(reader: Reader<S, A>): Source<A> =
    Source { reader(read()) }

/**
 * Storage * Writer -> Dispatcher. The Writer is applied to the current stored
 * value AT DISPATCH TIME. It is not executed while composing the operation.
 */
operator fun <S, A> Storage<S>.times(writer: Writer<S, A>): Dispatcher<A> =
    Writer { command ->
        { _: Unit ->
            val updated = writer(command)(read())
            write(updated)
        }
    }

/** Construct a live Source reading a Storage. */
@Suppress("FunctionName")
fun <S> Read(storage: Storage<S>): Source<S> = Source { storage.read() }

/** Construct a Dispatcher replacing the entire stored value. */
@Suppress("FunctionName")
fun <S> Dispatch(storage: Storage<S>): Dispatcher<S> =
    Writer { value -> { _: Unit -> storage.write(value) } }
