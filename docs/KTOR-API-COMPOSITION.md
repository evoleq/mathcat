# `mathcat-ktorx`: Ktor adapter for composed API descriptions

**Design note · 18 November 2026 · draft, not implemented.**

## The dependency boundary

The reusable DSL for `Api`, `group` and `module` is **independent of Ktor**. Its design and contracts are specified in [API module composition](API-MODULE-COMPOSITION.md). These are HTTP endpoint descriptions, not live Ktor routing objects.

**Only the dedicated `mathcat-ktorx` Gradle module (the `ktorx` adapter) knows Ktor.** Its Kotlin package will be `org.evoleq.mathcat.ktorx`. It consumes the assembled API description and connects each typed endpoint to a Ktor route and an Action workflow. This belongs beside the existing `Receive` and `Respond` integration. The dependency points **from the Ktor adapter to the API descriptions**, never the reverse.

The existing [API implementation](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/ktorx/api/api.kt) already defines `group` / `module` without Ktor-specific routing calls. Separately, [backend routes](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-backend/src/main/kotlin/org/solyton/solawi/bid/module/bid/routing/routing.auction.kt) repeat `post("create")` and `... runOn Base(call, environment)`. The proposed adapter links these **existing** mechanisms without duplicating endpoint identity.

## Illustrative Ktor-only syntax (not an implemented API)

```kotlin
// 'api' was assembled elsewhere, with no Ktor dependency.
routing {
    installApi(api, environment) {
        handle(CreateAuctionEndpoint) {
            ReceiveContextual<CreateAuction>() *
                IsGrantedInSpecialContext("CREATE_AUCTION") *
                CreateAuction *
                Respond<Auction> { transform() }
        }
    }
}
```

The handler is associated with the endpoint definition: Ktor method/path are obtained from the resolved descriptor, **not repeated separately** in a `post("create")` literal. It can access `origin = (moduleId, endpointId, method, resolvedPath)` to identify which original API endpoint it implements. This remains descriptive metadata; the real authorization check must still enforce permissions.

The adapter captures the chosen application environment when installing routes, but it must create `Base(call, environment)` **separately for each Ktor request** and invoke the composed Action once. Removing this repeated boilerplate may not cause a cached `ApplicationCall`, a global coroutine context, or altered database connection/transaction semantics. The application and Ktor retain scheduling and request lifecycle ownership.

## Module boundary and minimal coupling

```text
mathcat-api (proposed small Ktor-free API description module)
    └── Api / Endpoint / group / module / typed identity / origin
          ↑ depends on
mathcat-ktorx (separate Ktor adapter module)
    └── route installation / handler binding / Receive / Respond / per-request Base
          ↓ uses Ktor's request lifecycle
        Ktor
```

The module names follow MathCat's Gradle module prefix convention. `mathcat-api` remains a **proposed** extraction for the reusable API declarations; it could first be a Ktor-free package if that is simpler. `mathcat-ktorx` is the explicitly chosen **home of all Ktor-specific abstractions**. Neither `mathcat-core`, `mathcat-store`, `mathcat-actions` nor the reusable API declarations should acquire a Ktor dependency. In particular `group` and `module` are not methods on Ktor's `Route`.

- The API DSL depends on no Ktor types.
- `mathcat-ktorx` depends on the compiled API descriptor interface and only the smallest execution contract necessary (possibly `mathcat-actions`), rather than pulling in the whole application. Keep Ktor dependencies internal to this module.
- Feature modules need not import Ktor merely to declare `group`, `module`, endpoints or origin metadata.
- Other interpreters (tests, docs, clients, alternative servers) can consume the same descriptors later without changing the API DSL.
- No JavaFX/Swing integration or Flow runtime is part of this slice. No changes to Solyton.

## Focused verification

1. Compose API fragments and detect collisions **without** Ktor.
2. Resolve a nested endpoint and verify its original module, ID, method and canonical path.
3. Install a Ktor route by **the same endpoint identity**; report missing or duplicate handlers.
4. Run two separate requests in a Ktor test application, proving each gets its own `ApplicationCall` while sharing the intended environment.
5. Compare success, authorization failure, serialization error and cancellation behavior with the current explicit `runOn Base(call, environment)` routes.

Start with a narrow adapter proof of concept. Do not let the conceptual analysis force a generic runtime, new framework integrations, or unnecessary dependencies.

**Implementation status:** The `mathcat-ktorx` module and its sources have **not** been created by this documentation-only research PR. Introduce the build module when implementing the first small Ktor adapter and verify the chosen Ktor version/target setup separately.
