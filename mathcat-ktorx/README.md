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


## Contextual + IO: the Solyton composition vocabulary

The server JVM adapter now retains the Solyton-shaped Contextual(userId: UUID, context: String, data: T) with its map operation, and a Ktor-only suspendable Action/ KlAction composition. The pure MathCat Core State remains non-suspend.

The KtorIO<Base> interpreter takes a getter for the application's actual Base.call, a composed Serializers registry, a Result JSON codec, optional authenticated-user resolver (defaults to Ktor JWTPrincipal subject) and independent ExceptionTransforms.

```kotlin
val io = KtorIO<MyBase>(
    callOf = { it.call },
    serializers = applicationSerializers,
    transforms = applicationExceptionTransforms
)
routing {
    bindEndpoint(applicationApi, CreateAuction::class, environment, ::Base) { base ->
        with(io) {
            (ReceiveContextual<CreateAuction>() * checkPermission * createAuction *
                Respond<Auction> { environment.transformException(this) }) runOn base
        }
    }
}
```

All four original IO modes exist: Receive<T>() decodes JSON into Result<T>, Receive(value) injects constant input; ReceiveContextual<T>() authenticates the caller and attaches the mandatory Context header and request data; ReceiveContextual(params) transforms query parameters; Respond<T>() serializes the old Result envelope and converts exceptions using the composed error policy. Transform and TransformContextual lift transformations into the suspendable state chain. The provided lambda to Respond<T> can still use the familiar trailing form { transform() } by supplying a compatible exception transformer.

The client-side Contextual(context,data) remains a separate portable type in the context.data package (as in Solyton), used by requestResultEndpoint and sendResultEndpoint. Those methods send/receive typed Result envelopes with explicit serializers from manual or KSP-generated registries, preserve Context response headers and return an HttpStatusMessage on non-2xx responses.

**Compatibility boundaries:** The existing Solyton global registry is replaced by injected composition; its multi-purpose Base(call,database) is still the consumer's responsibility through callOf and createBase. The new Respond writes JSON Result failure envelopes uniformly, including status failures, whereas some legacy Ktor routes returned plain text or a directly serialized Failure.Message. This difference is visible and requires an explicit migration decision. The default principal resolver needs Ktor JWT authentication configured; tests can inject a replacement and never bypass authentication in production by default. No Solyton code has been changed.
