# toolkit-result — portable Evoleq Result

This module extracts the author's `Result.Success / Result.Failure` algebra and operator names (`map`, `bind`, `multiply`, `apply`, `Return`, and suspend variants) into a pure Kotlin Multiplatform module.

It has no dependencies on Ktor, Serialization, HTTP clients or the application. The former `Result.Failure.HttpStatusMessage` is retained as a case, but now carries a **numeric HTTP status code (`Int`) rather than Ktor's `HttpStatusCode`**. Converting to/from Ktor's status class belongs at the future `toolkit-ktorx` boundary; this is not a source-compatible drop-in for that constructor.

```kotlin
val number: Result<Int> = Result.Return(7)
val computed = number bind { Result.Success(it + 1) }
val error = Result.Failure.Message("Invalid request")
```

As in the original operators, ordinary exceptions raised by transformations become `Result.Failure.Exception`. **CancellationException is rethrown**, preserving coroutine cancellation instead of turning it into an ordinary application failure. Tests cover both pure and suspend cases and the original operation vocabulary.

Serialization of `Result<T>`, the legacy wire shape (`type`, `classType`, `data`, `message`) and custom serializer registries are separate work in `toolkit-serialization`; do not assume this module serializes itself. No code here depends on the still-Draft `toolkit-api` or changes Solyton.

## Composable exception-to-HTTP-error transformations

Solyton currently uses a large application-specific `Result.Failure.Exception.transform()` function. The framework-free `ExceptionTransforms` policy retains the same outcome (status + message), but lets each feature own its mapping:

```kotlin
val authenticationErrors = ExceptionTransforms {
    on<InvalidToken>(401)
}

val bankingErrors = ExceptionTransforms {
    on<BankAccountMissing>(404)
}

val applicationErrors = ExceptionTransforms {
    include(authenticationErrors)
    include(bankingErrors)
    otherwise(500) { "Internal server error" }
}

val response = applicationErrors.transform(
    Result.Failure.Exception(BankAccountMissing())
)
```

This returns an `ErrorResponse(statusCode: Int, message: Result.Failure.Message)`. The actual Ktor `HttpStatusCode` conversion lives in `toolkit-ktorx`; the Result module stays Ktor-free. Registrations are ordered (first matching rule wins, including inheritance); duplicate exact exception classes are rejected. Explicit `otherwise` sets an application-wide fallback. A separate Ktor adapter will expose the familiar `Respond<T> { transform() }` style. No Solyton migration.
