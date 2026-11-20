# toolkit-context — pure authenticated Contextual (JVM)

The original Solyton backend declares:

```kotlin
data class Contextual<T>(
    val userId: UUID,
    val context: String,
    val data: T
)
```

This module deliberately preserves the `java.util.UUID` contract, but does **not** depend on Ktor, JWT, a database or a server call. `Contextual.map` transforms `data` while preserving `userId` and `context`.

**Current platform boundary:** JVM. The older Evoleq common/client `Contextual<T>(context:String,data:T)` is a *different* type. A shared multiplatform identity representation must be decided explicitly rather than changing user IDs silently.
