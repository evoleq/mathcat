# mathcat-transform — Ktor-free Result/Contextual transformations (JVM)

```kotlin
val result: Result<Int> = Result.Success(3)
val transformed = Transform<Int, String> { it.toString() }.apply(result)

val contextual: Result<Contextual<Int>> = ...
val mapped = TransformContextual<Int, String> { it.toString() }
    .apply(contextual)
```

Both functions preserve `Result.Failure`; `TransformContextual` additionally preserves the original `Contextual.userId` and `Contextual.context`. They are **pure values**, reusable outside a server, independently testable and dependent only on `mathcat-result` and `mathcat-context`.

`mathcat-ktorx` supplies small `times` adapters to execute the same transformations in a suspendable request action; the transformations themselves do **not** import Ktor or receive an `ApplicationCall`.

This first faithful port is JVM because Solyton's authenticated `Contextual` uses `java.util.UUID`.
