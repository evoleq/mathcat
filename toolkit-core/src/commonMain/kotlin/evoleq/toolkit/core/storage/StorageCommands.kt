package evoleq.toolkit.core.storage

import evoleq.toolkit.core.reader.Source
import evoleq.toolkit.core.writer.Dispatcher
import evoleq.toolkit.core.writer.Writer

/**
 * Interpret an S-state Writer through a live Storage as a synchronous
 * command Dispatcher. Composition does not read or write Storage.
 *
 * On dispatch: read the current state once, apply the Writer, write the
 * result once. The read-modify-write sequence is NOT atomic.
 */
operator fun <S, A> Storage<S>.times(writer: Writer<S, A>): Dispatcher<A> =
    Writer { command ->
        { _: Unit ->
            val current = read()
            write(writer(command)(current))
        }
    }

/** Build a deferred Source from the existing read/write Storage capability. */
@Suppress("FunctionName")
fun <S> Read(storage: Storage<S>): Source<S> = Source { storage.read() }

/**
 * A synchronous Dispatcher that replaces the complete Storage value.
 * Its write effect occurs only on dispatch, never when constructed.
 */
@Suppress("FunctionName")
fun <S> Dispatch(storage: Storage<S>): Dispatcher<S> =
    Writer { value -> { _: Unit -> storage.write(value) } }
