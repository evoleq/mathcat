package evoleq.toolkit.core.reader.maps

import evoleq.toolkit.core.reader.Reader

/**
 * Map Reader constructor, matching the legacy Evoleq convention.
 *
 * A null key intentionally yields null, including for maps that permit a
 * null key. An absent key or a present null value also yields null.
 *
 * Kept in a separate package from the optics Get constructors so imports
 * can be explicit and aliased when necessary.
 */
@Suppress("FunctionName")
fun <K, V> Get(key: K?): Reader<Map<K, V>, V?> =
    Reader { source -> key?.let { source[it] } }

/**
 * Partial Map Reader constructor. Throws if the requested key does not
 * resolve to a non-null value (including missing or null-valued entries).
 */
@Suppress("FunctionName")
fun <K, V> GetForSure(key: K): Reader<Map<K, V>, V> =
    Reader { source -> source[key]!! }
