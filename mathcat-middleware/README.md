# MathCat Middleware – standalone `Call(action)`

This separately published Kotlin Multiplatform module builds a small,
composable frontend HTTP middleware **on top of** portable
`mathcat-actions` and `mathcat-ktorx`.

## Zero-noise action calls

Bind the endpoint key **once**, when declaring the Action. No KClass key,
`gateway.Call`, serializer or HTTP method appears at the call site:

```kotlin
val login = Action(
    name = "login",
    reader = loginInput,
    writer = updateLogin
).at(LoginKey::class)

val runtime = MiddlewareRuntime(
    storage = applicationStorage,
    client = ApiClientResolver { state ->
        ApiClient(
            client = state.client(state.user.isLoggedIn),
            api = state.api,
            baseUrl = state.environment.backendUrl,
            serializers = applicationSerializers
        )
    }
)

val response = Call(login) runOn runtime
```

The supplied `ApiClient` resolves request/response serializers from an immutable
registry. Use `Serializers { include(GeneratedSerializers) }` for KSP or register
them manually; callers never repeat serializers. `Call` derives the HTTP method
and URL from `Api[endPoint]`, not from a second route definition. For a
parameterized route, attach a pure `pathParameters: Reader<State, Map<String,
String>>` when binding the Action.

`MiddlewareRuntime` does not cache requests: each `runOn` reads Storage
once, resolves the current environment/authenticated client and evaluates the
Action's pure input Reader against the same snapshot.

This is a *suspendable middleware effect*, not a fake pure `State`; its
`map`/`flatMap` support effect composition. The Action's Writer/onSuccess
is not invoked implicitly. HTTP client choice and error policy remain with
the application. Generic type serializers still require an explicit adapter
until the registry supports parameterized types.

## Module boundary

Only applications that use HTTP middleware need
`com.github.evoleq.mathcat:mathcat-middleware:<release>`.
Neither `mathcat-actions` nor `mathcat-api` gains an HTTP dependency.
The lower-level Ktorx `ApiClient` and its explicit-codec overloads remain.
