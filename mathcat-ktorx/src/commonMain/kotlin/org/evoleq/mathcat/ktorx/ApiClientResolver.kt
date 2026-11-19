package org.evoleq.mathcat.ktorx

import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.storage.Storage
import org.evoleq.mathcat.ktorx.context.data.Contextual
import org.evoleq.mathcat.result.Result
import kotlin.reflect.KClass

/**
 * Application-owned, request-time resolver for the frontend HTTP boundary.
 *
 * In Solyton an Application's user state determines which HttpClient to use
 * while environment / API are read through Storage and optics. Keeping this
 * resolver separate allows client/auth/URL changes without retaining a stale
 * HttpClient in an Action or injecting Solyton classes into MathCat.
 */
fun interface ApiClientResolver<State> {
    fun resolve(state: State): ApiClient
}

/**
 * A suspending analogue of the old frontend Call's *transport* stage.
 *
 * Reads the Storage exactly once at invocation; resolves client/endpoint base
 * from the same snapshot, then interprets the Action's pure Reader. No implicit
 * write or claim of pure KlState semantics. Explicit serializer overloads remain
 * available on lower-level client functions for special/generic payloads.
 */
@Suppress("FunctionName")
suspend inline fun <State, reified S : Any, reified T : Any> ApiClientResolver<State>.Call(
    key: KClass<*>,
    storage: Storage<State>,
    reader: Reader<State, S>,
    pathParameters: Map<String, String> = emptyMap()
): Result<Contextual<T>> {
    val snapshot = storage.read()
    return resolve(snapshot).Call<State, S, T>(key, snapshot, reader, pathParameters)
}
