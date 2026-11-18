# mathcat-api — composed HTTP API declarations

This module supplies a **Ktor-independent**, Kotlin Multiplatform description of HTTP API endpoints. It has no runtime dependencies on Ktor, databases, coroutines, application classes, `mathcat-core`, `mathcat-actions` or `mathcat-store`. The separate **`mathcat-ktorx`** module will eventually bind endpoints to Ktor routes.

## Feature declarations

```kotlin
val createAuction = Endpoint<CreateAuction, Auction>(
    id = "create",
    method = HttpMethod.POST,
    path = "create"
)

val auctionsApi = api("auctions") {
    group("management") {
        endpoint(createAuction)
    }
}
```

This is compile-time type association, not a runtime serializer or validator. A feature owns its Endpoint instance and shares that same reference with consumers that bind it to routes.

## Compose API modules without a server

```kotlin
val applicationApi = api("application") {
    module("/api/v1/auctions", auctionsApi)
    module("/api/v1/users", usersApi)
}

val declared = applicationApi.requireEndpoint(createAuction)
// declared.path == "/api/v1/auctions/create"
// declared.method == HttpMethod.POST
// declared.origin == EndpointOrigin("auctions", listOf("management"))
```

**`group`** adds logical grouping metadata; it does **not** modify the URL. **`module`** mounts a previously constructed `Api` at a path prefix. Both operations can be nested, and the endpoint keeps its **original module ID** when imported. Paths are canonicalized to one leading slash, no trailing slash and no duplicate slashes. Empty paths denote `/`; queries/fragments and parent-directory segments are rejected.

## Typed endpoint identity and deterministic validation

An `Endpoint<Request,Response>` is an **identity-bearing instance**. To resolve an endpoint from an assembled API, use that very same object:

```kotlin
val bound: ResolvedEndpoint<CreateAuction, Auction> =
    applicationApi.requireEndpoint(createAuction)
```

A newly constructed endpoint with the same string ID does **not** resolve. This avoids manufacturing a request/response type pairing through an unchecked `KClass` cast.

Assembly fails before publishing an `Api` on:

- duplicate logical identities `(origin.moduleId, endpoint.id)`;
- a declaration instance mounted multiple times (an ambiguous typed binding);
- duplicate `(HTTP method, canonical path)` routes.

Different methods can share a path. An `Api` is a validated snapshot; independently built source fragments are unaffected by mounting.

## Limits and next step

- This **initial DSL is not source-compatible** with the legacy `Api` builder or its `KClass` registry. Its existing vocabulary and role guided the design.
- It does not yet bind handlers, decode payloads, check authorization, execute requests or represent codecs. No claim of fully verified end-to-end type safety is implied.
- The route interpreter belongs in **`mathcat-ktorx`**, consuming this module, never the reverse.
- Reusing an identical declaration at multiple mount points is intentionally disallowed until an explicit namespacing/multi-binding policy is designed. A later design may need to distinguish declaration from mount instance.
- For now the API is HTTP-aware but **framework-independent**, not a general transport algebra.

## Tests

`commonTest` exercises independent contributions, nested associativity of mount paths, logical groups, normalization, duplicate identity/route rejection, identity-safe typed lookup and snapshot behavior. The module is declared for the same platform matrix as the existing MathCat modules; actual cross-platform results are reported by CI.
