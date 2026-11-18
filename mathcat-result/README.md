# mathcat-result — portable Evoleq Result

This module extracts the author's `Result.Success / Result.Failure` algebra and operator names (`map`, `bind`, `multiply`, `apply`, `Return`, and suspend variants) into a pure Kotlin Multiplatform module.

It has no dependencies on Ktor, Serialization, HTTP clients or the application. The former `Result.Failure.HttpStatusMessage` is retained as a case, but now carries a **numeric HTTP status code (`Int`) rather than Ktor's `HttpStatusCode`**. Converting to/from Ktor's status class belongs at the future `mathcat-ktorx` boundary; this is not a source-compatible drop-in for that constructor.

```kotlin
val number: Result<Int> = Result.Return(7)
val computed = number bind { Result.Success(it + 1) }
val error = Result.Failure.Message("Invalid request")
```

As in the original operators, ordinary exceptions raised by transformations become `Result.Failure.Exception`. **CancellationException is rethrown**, preserving coroutine cancellation instead of turning it into an ordinary application failure. Tests cover both pure and suspend cases and the original operation vocabulary.

Serialization of `Result<T>`, the legacy wire shape (`type`, `classType`, `data`, `message`) and custom serializer registries are separate work in `mathcat-serialization`; do not assume this module serializes itself. No code here depends on the still-Draft `mathcat-api` or changes Solyton.
