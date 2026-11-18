package org.evoleq.mathcat.core.storage

/**
 * A plain mutable, non-observable in-memory Storage implementation.
 *
 * Memory does not integrate with Compose snapshots, keep values across
 * compositions automatically, synchronize concurrent updates, or persist
 * values to durable storage. A different backend can expose the same
 * Storage.read()/write() operations with different observation semantics.
 */
class Memory<S>(initial: S) : Storage<S> {
    private var current: S = initial

    override fun read(): S = current

    override fun write(value: S) {
        current = value
    }
}
