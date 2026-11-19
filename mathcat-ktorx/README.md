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

## Ergonomic server and frontend bindings (Issue #17)

The opt-in `installApi` DSL interprets the original `Api` endpoint keys.
It validates routes before registering handlers, installs a composed serializer
registry once and constructs an independent `RequestContext(call, environment)`
per HTTP request. The context is Ktor-specific; **it is not the mathematical
Boundary or a replacement for Domain ports**.

```kotlin
val api = Api { post<String, String>(Greeting::class, "greeting") }
val codecs = Serializers { include(GeneratedFeatureSerializers) } // or manual

routing {
    installApi(api, environment, codecs) {
        handle(Greeting::class) { base ->
            (Receive<String>() *
                Transform<String, String> { base.environment.greet(it) } *
                Respond<String>()) runOn base
        }
    }
}
```

`Receive<String>()` and `Respond<String>()` now obtain their codecs from the
installed registry: **no explicit serializer at the call site**. Supplying a
generated registry is optional; custom serializers and parameterized types
can still use the explicit overloads.

### Frontend: State Reader / Action interpreter

The transport-independent `mathcat-actions.Action` deliberately has no HTTP
method, KClass endpoint or serializer. Its pure `reader` can be interpreted
at a frontend boundary without changing its structure:

```kotlin
val apiClient = ApiClient(
    client = application.client(isLoggedIn), // chosen by the application
    api = applicationApi,
    baseUrl = environment.backendUrl,
    serializers = Serializers { include(GeneratedFeatureSerializers) }
)

val result: Result<Contextual<LoggedIn>> =
    apiClient.Call(ApplicationLogin::class, applicationState, loginAction.reader)
// In application code, decide whether to apply loginAction.writer / onSuccess.
```

For direct calls, use `apiClient.request<T>(key, pathParameters, queryParameters)`
and `apiClient.send<S, T>(key, payload, pathParameters)`. They select codecs
automatically from the provided registry, including KSP-generated registries.
`Call` derives GET query parameters from the existing `Api.Parameters`
contract; other supported methods send JSON request bodies. Authentication
and base URL remain application environment concerns; a configured HttpClient
provides authentication headers and custom client plugins.

For login-aware applications that hold the current state in `Storage`, use a
request-time resolver (instead of closing over a potentially stale client):

```kotlin
val gateway = ApiClientResolver<Application> { app ->
    ApiClient(
        client = app.client(app.userData.seemsToBeLoggedIn()),
        api = app.api,
        baseUrl = app.environment.backendUrl,
        serializers = applicationSerializers
    )
}
val result = gateway.Call<Application, Login, LoggedIn>(
    LoginKey::class, applicationStorage, loginAction.reader
)
```

The resolver reads Storage **once per call**, and the same snapshot determines
the selected client, environment and pure Reader input. A caller can use
`Storage * Lens` to assemble and focus those dependencies; the application
alone owns its authentication policy, ports and state updates.

**Important:** `Call` is a suspending IO interpreter, not a fake
`KlState<Storage<Application>, ...>`: pure core State cannot execute network
requests without changing its semantics. No global registry, serializer lookup
by a server-provided class name or automatic Action.writer mutation is added.

### Standalone middleware publication

For the shortest frontend expression, use the separately published
[`mathcat-middleware`](../mathcat-middleware) module. Bind the endpoint key
**once** at Action declaration with `action.at(LoginKey::class)` and then
write only `Call(action) runOn runtime`. The standalone `Call` is not an
`ApiClient`/gateway member; its client and serializers are injected by the
execution context. The original transport-neutral Action is unchanged.
