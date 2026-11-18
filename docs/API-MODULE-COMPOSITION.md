# Framework-independent API modules and groups

**Architecture research · 18 November 2026 · not an implemented MathCat API.**

The existing Evoleq `Api` DSL offers `group` and `module` declarations. **Neither operation requires Ktor.** Their job is to assemble reusable *descriptions* of an HTTP API, not to install server routes, manage requests or execute effects.

> Framework-independent is not the same as protocol-independent. An endpoint with HTTP methods and URL paths is an HTTP description; it can still be consumed by Ktor, another server framework, a client generator, tests or documentation without depending on any of them.

## Minimal dependency direction

```text
feature-A API definitions ─┐
feature-B API definitions ─┼─> API description + composition (no Ktor)
feature-C API definitions ─┘              ↑
                                          │ consumes descriptors
                           Ktor routing adapter (separate)
                                          │
                               Ktor request lifecycle
```

The API description/composition layer should have at most Kotlin standard-library dependencies, and **no Ktor, coroutine, database, Compose or application dependency**. It should not depend on `mathcat-store` or `mathcat-actions` merely to combine endpoint metadata. Whether this remains a small independent package or is extracted into a `mathcat-api` module is a later build/packaging decision. A new generic Boundary framework is **not** a prerequisite.

## Illustrative DSL (not implemented)

```kotlin
// The module owns declarations, not a Ktor Route.
val auctions = apiModule("auctions") {
    group("admin") {
        post<CreateAuction, Auction>(CreateAuctionEndpoint, "create")
    }
}
val users = apiModule("users") {
    get<GetUsers, Users>(GetUsersEndpoint, "all")
}

// Any consumer may compose the descriptions without a web server.
val api = api {
    module("auction", auctions)
    module("users", users)
}
```

Prefer adaptation of the **existing** `Api.group` / `Api.module` semantics over a wholesale rewrite. Keep the surface small and preserve familiar Kotlin DSL ergonomics. The above is one possible vocabulary, **not** a proposal to introduce all of these constructors immediately.

A resolved endpoint carries `(endpointId, moduleOrigin, method, canonicalPath, requestType, responseType)`. The declaration's **provenance** survives grouping and nested mounting, but no `ApplicationCall` or handler exists in this layer.

## Composition contracts to test

- Combining independent API modules preserves every distinct endpoint; nested mounts preserve source module/endpoint identity.
- Grouping and path composition have documented semantics and consistent normalization, including slashes.
- A duplicate endpoint identity or collision on (method, canonicalPath) is reported deterministically rather than silently overwriting a `HashMap` entry.
- Type-safe endpoint lookup never relies on pretending that an unchecked `KClass` cast proves a request/response pairing. The existing registry's unchecked lookup motivates tests and gradual improvement.
- Equivalent valid groupings resolve to equivalent endpoint descriptions. If the API uses mutable builders, they produce a validated immutable result before consumers use it.
- A module can be declared and tested **without any Ktor dependency or running server**.

A transport-specific interpreter is optional. The Ktor integration is described in [Ktor route adapter](KTOR-API-COMPOSITION.md). Keep the author’s original `Boundary` dependency-injection insights conceptually separate; API grouping does not need to become a full-scale architecture framework.
