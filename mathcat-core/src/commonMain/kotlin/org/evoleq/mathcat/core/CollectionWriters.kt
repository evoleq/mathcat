package org.evoleq.mathcat.core

/**
 * Reusable pure list writers, with the same contravariant API as any Writer.
 *
 * Unlike the historic predicate-based update helpers, keyed updates reject
 * duplicate keys rather than silently selecting the first match or adding duplicates.
 * They preserve existing positions and append only genuinely new keys.
 */

fun <A> append(): Writer<List<A>, A> =
    Writer { value -> { source -> source + value } }

fun <A> appendAll(): Writer<List<A>, List<A>> =
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
fun <A, K> listUpdateAllBy(keyOf: (A) -> K): Writer<List<A>, List<A>> =
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
fun <A, K> listUpsertAllBy(keyOf: (A) -> K): Writer<List<A>, List<A>> =
    Writer { updates ->
        { source ->
            requireDistinctKeys(source, keyOf, "source")
            requireDistinctKeys(updates, keyOf, "updates")
            val keys = source.map(keyOf).toSet()
            val replaced = listUpdateAllBy(keyOf)(updates)(source)
            replaced + updates.filter { keyOf(it) !in keys }
        }
    }

/** Readable domain-specific facades over the general Lens * Writer operation. */
fun <S, A, K> Lens<S, List<A>>.updateAllBy(
    keyOf: (A) -> K
): Writer<S, List<A>> = this * listUpdateAllBy(keyOf)

fun <S, A, K> Lens<S, List<A>>.upsertAllBy(
    keyOf: (A) -> K
): Writer<S, List<A>> = this * listUpsertAllBy(keyOf)

fun <S, A> Lens<S, List<A>>.add(): Writer<S, A> = this * append()
