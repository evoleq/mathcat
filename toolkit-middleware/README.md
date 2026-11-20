# ToolKit Middleware – standalone `Call(action)`

This separately published Kotlin Multiplatform module builds a small,
composable frontend HTTP middleware **on top of** portable
`toolkit-actions` and `toolkit-ktorx`.

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
`com.github.evoleq.mathcat:toolkit-middleware:<release>`.
Neither `toolkit-actions` nor `toolkit-api` gains an HTTP dependency.
The lower-level Ktorx `ApiClient` and its explicit-codec overloads remain.

## Composable middleware steps (no process management)

The frontend pipeline mirrors the useful part of Solyton's middleware:

```kotlin
val pipeline = Read(action) *
    Call(action) *
    OnError(action, featureFailurePolicy) *
    Dispatch(action) *
    React(action)

val result: Result<Payload> = pipeline runOn runtime
```

These are independently replaceable typed suspendable **Kleisli steps**
`MiddlewareStep<State, Input, Output>`. One Storage snapshot is captured at
pipeline start and is shared by `Read` and the client resolver in `Call`.
`Dispatch` maps `Result<Contextual<T>>` to `Result<T>`, optionally passes
the response context to a supplied callback, and applies the Action's Writer
on success. `React` applies the optional `onSuccess` Writer and any supplied
feature reaction on success; it never dispatches follow-ups implicitly.

`OnError` accepts an application-provided `FailurePolicy`; by default the
Failure is unchanged. No status-code fudging, UI dialogs or hidden coroutine
scopes are introduced. A `Debug` observer stage is similarly optional.

`ReactEnvelope(envelope, dispatcher)` runs `next` and `nextLazy` only after
success, preserving envelope values and order, and waits for the injected
`ActionDispatcher` to accept each follow-up. It does **not** register,
finish, or clear processes; that lifecycle will be a **separate module later**.
Neither exactly-once delivery nor process completion is implied by dispatch.

Storage updates are standard non-atomic read-modify-write: callers with
concurrent writers must provide stronger synchronization in their Storage.
No background `CoroutineScope(Job())` is created.

The lower-level standalone `Call(action) runOn runtime` remains valid, and
no extra gateway method is required.
