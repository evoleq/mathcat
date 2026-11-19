package org.evoleq.mathcat.ktorx

/** Resolve the current frontend HttpClient, API and serializers at call time.
 * The separate mathcat-middleware module owns the public Call(action) DSL.
 */
fun interface ApiClientResolver<State> {
    fun resolve(state: State): ApiClient
}
