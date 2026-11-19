package org.evoleq.mathcat.api

/**
 * The pure query-parameter value contract used by the original Api.get DSL.
 * The legacy client attaches kotlinx.serialization and URL encoding at its
 * transport boundary; neither is required to compose endpoint declarations.
 */
typealias QueryParams = List<Pair<String, String>>

abstract class Parameters {
    abstract val queryParams: QueryParams
}

abstract class EmptyParams : Parameters() {
    override val queryParams: QueryParams get() = emptyList()
}
