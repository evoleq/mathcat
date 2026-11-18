package org.evoleq.mathcat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionWritersTest {
    data class Entry(val id: Int, val name: String)
    data class Dashboard(val entries: List<Entry>)
    data class ApiResponse(val entries: List<Entry>)

    private val entriesLens = Lens<Dashboard, List<Entry>>(
        get = Reader { it.entries },
        set = Writer { newEntries -> { dashboard -> dashboard.copy(entries = newEntries) } }
    )

    private val initial = listOf(Entry(1, "one"), Entry(2, "two"))

    @Test
    fun updateAllPreservesPositionsAndIgnoresUnknownKeys() {
        val writer = listUpdateAllBy<Entry, Int> { it.id }
        assertEquals(
            listOf(Entry(1, "ONE"), Entry(2, "two")),
            writer(listOf(Entry(1, "ONE"), Entry(3, "three")))(initial)
        )
    }

    @Test
    fun upsertAllPreservesExistingOrderAndAppendsNewKeys() {
        val writer = listUpsertAllBy<Entry, Int> { it.id }
        assertEquals(
            listOf(Entry(1, "one"), Entry(2, "TWO"), Entry(3, "three")),
            writer(listOf(Entry(3, "three"), Entry(2, "TWO")))(initial)
        )
    }

    @Test
    fun duplicateBusinessKeysAreRejectedInSourceAndUpdates() {
        val writer = listUpsertAllBy<Entry, Int> { it.id }
        assertFailsWith<IllegalArgumentException> {
            writer(listOf(Entry(3, "first"), Entry(3, "second")))(initial)
        }
        assertFailsWith<IllegalArgumentException> {
            writer(listOf(Entry(3, "three")))(initial + Entry(2, "duplicate"))
        }
    }

    @Test
    fun composingLensHelperAndContraMapUpdatesApplicationState() {
        val writer: Writer<Dashboard, ApiResponse> =
            entriesLens.upsertAllBy(Entry::id)
                .contraMap { response: ApiResponse -> response.entries }
        val updated = writer(ApiResponse(listOf(Entry(2, "changed"), Entry(3, "new"))))(
            Dashboard(initial)
        )
        assertEquals(
            Dashboard(listOf(Entry(1, "one"), Entry(2, "changed"), Entry(3, "new"))),
            updated
        )
    }

    @Test
    fun simpleAppendComposesWithLens() {
        val writer: Writer<Dashboard, Entry> = entriesLens.add()
        assertEquals(
            Dashboard(initial + Entry(3, "three")),
            writer(Entry(3, "three"))(Dashboard(initial))
        )
    }
}
