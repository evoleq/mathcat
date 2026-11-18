# mathcat-ktorx — thin Ktor bindings for the *existing* Evoleq Api

**Stacked Draft PR based on MathCat API [PR #8](https://github.com/evoleq/mathcat/pull/8).** The API module is the source of truth: `Api { ... }`, its `KClass` keys, `EndPoint.Get/Post/Put/Patch/Delete/Head`, paths and groups remain unchanged.

## Ktor client (Multiplatform)

```kotlin
val api = Api { module("users") { get<ReadUser, User>(ReadUser::class, "{userId}") } }

// Explicit serializer, supplied directly or from manual/generated Serializers:
val user = client.requestEndpoint(
    api, ReadUser::class, "https://example.test",
    responseSerializer = User.serializer(),
    pathParameters = mapOf("userId" to "42")
)
```

The client reads the **existing method and URL** from `api[key]`, substitutes named path parameters (encoding each segment), passes query parameters to Ktor, and decodes with the given `KSerializer<T>`. The overload `sendEndpoint` also encodes a request body with `KSerializer<S>`. No runtime dependency on KSP, MathCat Actions or a global registry. The caller can use a manually assembled or KSP-generated registry to provide the codecs once those modules are integrated.

## Ktor server (JVM only)

```kotlin
routing {
    bindEndpoint(appApi, CreateAuction::class, environment, ::Base) { base ->
        // The existing composed Action workflow can run on the new per-call base.
        // ReceiveContextual * authorize * action * Respond runOn base
    }
}
```

The adapter reads the method/path from the same `EndPoint` declaration and constructs `Base(call, environment)` once **for each incoming request**. It does not reinterpret `Api.group`, cache calls, own database transaction lifetimes or introduce a generic Flow runtime.

## Scope and evidence

A portable MockEngine client test and a JVM `testApplication` route test verify endpoint method/path binding, named parameters, request body serialization, and two distinct request-local contexts. CI covers the same KMP targets as MathCat API.

**This is an initial adapter, not a migration**: it has not yet wired `mathcat-result`'s JSON envelope, authentication/authorization, serializers registry composition at the application boundary, or Solyton's full `ReceiveContextual` and `Respond` flow. Those are follow-ups after PRs #8, #9 and #10 are accepted and merged; no Solyton changes are made.

## Solyton-style Contextual IO (stacked Draft PR #13)

`Receive`, `ReceiveContextual`, `Context`, and `Respond` are JVM/Ktor-specific adapters over a request-local `RequestBase(call, environment)`. They accept codecs and authentication logic explicitly. The pure transformation parts live in **`mathcat-context`** and **`mathcat-transform`**, which have *no* Ktor dependency.

```kotlin
import org.evoleq.mathcat.transform.Transform

post("/echo") {
    val workflow = Receive<Unit, String>(String.serializer()) *
        Transform<String, String> { it.uppercase() } *
        Respond(resultStringSerializer)
    workflow runOn RequestBase(call, Unit)
}
```

`ReceiveContextual` has body and query-parameter variants. Both accept an application-supplied `identity: suspend (ApplicationCall) -> Result<RequestIdentity>` returning the authenticated UUID plus context; they short-circuit on failure. `TransformContextual` maps only the contextual value, not identity or tenant context. `Respond` accepts `KSerializer<Result<T>>`, an optional context response header, and `ResponsePolicy` to map exceptions and HTTP status failures.

**Not yet wired here:** Solyton's concrete JWT principal/authorization/DB setup, automatic serializer lookup from PR #9, or final `ResultSerializer` wire-compatibility from PR #12. This branch copies the still-unmerged `mathcat-result` module from PR #10; that duplication must be removed by rebase before integration. The original `Api` DSL and the pure Core `State` remain unchanged.
