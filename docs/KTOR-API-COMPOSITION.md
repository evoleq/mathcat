# Composing the existing API DSL into Ktor routes

**Design note · 18 November 2026 · draft, not implemented.**

## Motivation from the actual code

In [the existing `Api` DSL](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/ktorx/api/api.kt), a feature can declare endpoint metadata with `post/get/patch/...`, `group(name)` and `module(path, ...)`. The registry uses mutable `HashMap<KClass<*>, EndPoint<*,*>>` and a typed lookup performs an unchecked cast. In [the backend routing DSL](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-backend/src/main/kotlin/org/solyton/solawi/bid/module/bid/routing/routing.auction.kt), the Ktor `post("create") { ... }` path is declared separately from that metadata. Almost every handler repeats `... runOn Base(call, environment)`.

**Author request:** allow several feature modules to contribute API declarations; at the Ktor route declaration, make the owning API endpoint visible; and centralize the repeated request-base assembly. This is a **Ktor integration**, like `Receive` and `Respond`, not a proposal to turn the whole application into a Flow.

## Sketch — notation only, NOT an available Kotlin API

```kotlin
// Owning feature module:
val auctionsApi = apiModule("auctions", prefix = "/auctions") {
    post<CreateAuction, Auction>(CreateAuctionEndpoint, path = "create")
}
val usersApi = apiModule("users", prefix = "/users") {
    get<GetUsers, Users>(GetUsersEndpoint, path = "all")
}

// Application assembly: no feature needs to know the other features.
val appApi = api {
    module(auctionsApi)
    module(usersApi)
}

// Ktor interpreter (the handler sees its endpoint identity):
routing {
    installApi(appApi, environment) {
        handle(CreateAuctionEndpoint) {
            ReceiveContextual<CreateAuction>() *
                IsGrantedInSpecialContext("CREATE_AUCTION") *
                CreateAuction *
                Respond<Auction> { transform() }
        }
    }
}
```

An adapter is free to choose a more idiomatic Kotlin DSL once a small prototype is compiled and tested. The important goal is to **bind route and endpoint using the same typed identity**, not repeat their HTTP method/path as disconnected strings. A route should be able to expose `origin = (moduleId, endpointId, method, resolvedPath)` for debugging, routing audits and documentation, including when a module is mounted inside another module. The endpoint descriptor is not automatically a substitute for actual authorization.

## Semantics worth making explicit

- **Modularity:** each feature publishes an `Api` fragment; root assembly combines them. `group` (logical grouping) and `module` (mount path/ownership) should have clearly different semantics.
- **Composition:** nested mounts normalize paths consistently. For valid, conflict-free module trees, equivalent groupings of module composition should resolve to identical route descriptors and provenance.
- **Conflicts:** detect duplicate endpoint identities and collisions on (HTTP method, canonical path) with a diagnostic, not silent mutation/overwriting. Distinct methods may legitimately share a path.
- **Typing:** endpoint identity should preserve request/response types. Runtime codecs and request failures remain explicit; the old `KClass` registry/unchecked cast is a motivation to improve, not proof of type safety.
- **Handler association:** every Ktor route references a descriptor already declared in its module. Unbound descriptors and handlers should be reportable rather than silently ignored.
- **Per-request execution:** the Ktor adapter captures an application-level `environment`, but Ktor supplies a fresh `ApplicationCall` on each request. It should construct and invoke `Base(call, environment)` **inside the handler**. Eliminating the repeated text must *not* capture the first call, alter DB connection/transaction lifetime, or make `runOn` global.
- **Ownership:** Ktor continues to own event delivery, coroutine context, routing and lifecycle. We are extending a Ktor-specific DSL, not adding another scheduler or application-global Flow runtime.

The old route was:

```kotlin
post("create") {
    ReceiveContextual<CreateAuction>() *
        IsGrantedInSpecialContext("CREATE_AUCTION") *
        CreateAuction *
        Respond<Auction> { transform() } runOn Base(call, environment)
}
```

The intended new route is an **endpoint-associated** handler that returns or executes the same Action composition; the Ktor adapter supplies the base. `Base(call, environment)` remains a real execution requirement, simply no longer duplicated at every call site.

## Small verification slice, not a framework rewrite

1. Compose two API modules and one nested submodule, verify resolved paths and original endpoint ownership.
2. Verify rejected duplicate IDs and method/path collisions; no partial mutation.
3. Bind a route by endpoint identity, check its provenance and detect missing/duplicate handlers.
4. Exercise two independent Ktor test requests and show that each gets its own call, while both use the intended environment.
5. Check `Receive`, `Respond`, authorization, failure and cancellation behavior matches the existing route before replacing any code.

Keep Solyton unchanged while trying this in an isolated Ktor adapter/test project. Flow/Gap and topology are relevant to the *explanation* of boundaries, not required runtime machinery for these tasks.
