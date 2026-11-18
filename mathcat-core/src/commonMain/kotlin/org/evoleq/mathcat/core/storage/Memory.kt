package org.evoleq.mathcat.core.storage

import org.evoleq.mathcat.core.reader.Source

/**
 * In-memory Storage for tests, prototypes and explicitly single-threaded use.
 *
 * Its Source reads the latest stored value but does NOT register with Compose
 * snapshots, trigger recomposition, publish updates or implement persistence.
 *
 * The class is not thread-safe, atomic or immutable. Clients should prefer
 * immutable S values and publish a new value through write rather than mutate
 * an object in place. Do not confuse this with a Compose MutableState backend.
 */
class Memory<S>(initial: S) : Storage<S> {
    private var current: S = initial

    override val source: Source<S> = Source { current }

    override fun write(value: S) {
        current = value
    }
}
