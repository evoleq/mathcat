# `mathcat-ktorx`: thin Ktor adapter for the original Evoleq API DSL

**Design note · corrected 18 November 2026 · not implemented.**

## Dependency boundary

The original `Api` DSL, its `group`/`module` composition and new minimal `include`/`group(name, Api)` overloads are **Ktor-independent**. The [actual first implementation](https://github.com/evoleq/mathcat/pull/8) preserves the author's `Api { ... }` and `EndPoint.Get/Post/Put/Patch/Delete/Head` API rather than inventing a new descriptor system.

**All Ktor-specific abstractions belong in `mathcat-ktorx`** (planned Gradle module and package `org.evoleq.mathcat.ktorx`). The adapter consumes a declared `Api` and binds a Ktor Route to the same `KClass<*>` endpoint key used by existing clients. It also owns `Receive`, `Respond` and execution of the composed Action on a request-local `Base(call, environment)`.

The original [application routes](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-backend/src/main/kotlin/org/solyton/solawi/bid/module/bid/routing/routing.auction.kt) use `post("create") { ... runOn Base(call, environment) }`; their methods and paths can instead be read from the *existing* `EndPoint` metadata without changing the API DSL.

## Possible Ktor-only syntax — not implemented

```kotlin
// applicationApi is assembled in plain Kotlin from original Api fragments.
routing {
    installApi(applicationApi, environment) {
        handle(CreateAuction::class) {
            ReceiveContextual<CreateAuction>() *
                IsGrantedInSpecialContext("CREATE_AUCTION") *
                CreateAuction *
                Respond<Auction> { transform() }
        }
    }
}
```

The draft `handle` adapter resolves `applicationApi[CreateAuction::class]`, uses its method and URL and associates the Ktor Route with that endpoint key. The existing `group` field and mounted URL are available for diagnostics. Tracking the **original contributing Kotlin module**, which the old API does not explicitly model, is a *separate open design detail*. Do not introduce a new endpoint type or redesign the framework-free DSL as a prerequisite without another explicit decision.

The adapter captures an application environment at installation, but constructs `Base(call, environment)` **for each request**. It must not reuse an `ApplicationCall`, extend DB transactions implicitly or take over Ktor scheduling or lifecycle.

## Minimal coupling

```text
mathcat-api (implemented on Draft PR #8; Ktor-free)
  Api / EndPoint / group / module / include / KClass lookup
          ↑
mathcat-ktorx (planned; Ktor-only)
  route registration / Receive / Respond / per-request Base
          ↓
         Ktor
```

The adapter may depend on the minimal existing Action execution contract. `mathcat-api`, `mathcat-core`, `mathcat-store` and `mathcat-actions` must not gain Ktor dependencies. No general Flow framework, JavaFX/Swing adapter or Solyton migration belongs to this work.

## Focused tests for the later adapter

1. Resolve and bind an endpoint using the existing `KClass` key and `EndPoint` fields.
2. Verify each installed Ktor Route refers to its declared endpoint (method, URL, group).
3. Make two test requests and verify `Base(call, environment)` uses the corresponding request's `ApplicationCall`.
4. Compare authorization, failure, serialization and cancellation behavior with the existing routes.
5. Verify API composition and route installation do not introduce dependencies in the wrong direction.

**Status:** This design remains separate and unimplemented. The corrected `mathcat-api` PR #8 has green cross-platform CI but remains Draft. Do not merge/release without review.
