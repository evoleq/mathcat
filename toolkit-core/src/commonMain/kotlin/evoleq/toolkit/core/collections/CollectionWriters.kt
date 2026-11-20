package evoleq.toolkit.core.collections

import evoleq.toolkit.core.optics.Lens
import evoleq.toolkit.core.optics.times
import evoleq.toolkit.core.writer.Writer

/**
 * Reusable pure list writers, with the same contravariant API as any Writer.
 *
 * Unlike the historic predicate-based update helpers, keyed updates reject
 * duplicate keys rather than silently selecting the first match or adding duplicates.
 * They preserve existing positions and append only genuinely new keys.
 */

@Suppress("FunctionName")
fun <A> Append(): Writer<List<A>, A> =
    Writer { value -> { source -> source + value } }

@Suppress("FunctionName")
fun <A> AppendAll(): Writer<List<A>, List<A>> =
    Writer { values -> { source -> source + values } }

private fun <A, K> requireDistinctKeys(
    items: List<A>,
    keyOf: (A) -> K,
    label: String
) {
    val keys = mutableSetOf<K>()
    require(items.all { keys.add(keyOf(it)) }) {
        "Duplicate keys in $label"
    }
}

/**
 * Replace existing items by matching keys; ignore updates with unknown keys.
 * Duplicate keys in either list are rejected; no reordering takes place.
 */
@Suppress("FunctionName")
fun <A, K> UpdateAllBy(keyOf: (A) -> K): Writer<List<A>, List<A>> =
    Writer { updates ->
        { source ->
            requireDistinctKeys(source, keyOf, "source")
            requireDistinctKeys(updates, keyOf, "updates")
            val byKey = updates.associateBy(keyOf)
            source.map { old ->
                val key = keyOf(old)
                if (byKey.containsKey(key)) byKey.getValue(key) else old
            }
        }
    }

/**
 * Replace existing items by key and append unknown keys (in update order).
 * Duplicate keys in either list are rejected. Complexity is expected O(n + m)
 * with hash-based key lookup, not guaranteed for pathological hash collisions.
 */
@Suppress("FunctionName")
fun <A, K> UpsertAllBy(keyOf: (A) -> K): Writer<List<A>, List<A>> =
    Writer { updates ->
        { source ->
            requireDistinctKeys(source, keyOf, "source")
            requireDistinctKeys(updates, keyOf, "updates")
            val keys = source.map(keyOf).toSet()
            val replaced = UpdateAllBy(keyOf)(updates)(source)
            replaced + updates.filter { keyOf(it) !in keys }
        }
    }

/** Readable domain-specific facades over the general Lens * Writer operation. */
fun <S, A, K> Lens<S, List<A>>.updateAllBy(
    keyOf: (A) -> K
): Writer<S, List<A>> = this * UpdateAllBy(keyOf)

fun <S, A, K> Lens<S, List<A>>.upsertAllBy(
    keyOf: (A) -> K
): Writer<S, List<A>> = this * UpsertAllBy(keyOf)

fun <S, A> Lens<S, List<A>>.add(): Writer<S, A> = this * Append()
