# Framework-independent composition of the existing Evoleq API DSL

**Research context · corrected 18 November 2026.** The implementation slice is [Draft PR #8](https://github.com/evoleq/mathcat/pull/8). **Do not replace the original DSL.** The author explicitly requested *only* modularity; the first alternate Endpoint/ApiBuilder design was rejected and replaced by a faithful port.

## Source of truth: the original DSL

The existing [Evoleq `api.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/ktorx/api/api.kt) already provides `Api { ... }`, `EndPoint.Get/Post/Put/Patch/Delete/Head`, `Map<KClass<*>, EndPoint<*,*>>`, explicit/reified key overloads, `group(name) { ... }`, `module(path) { ... }`, and **`module(path, fragment: Api)`**. Its concrete [application use](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-api-data/src/commonMain/kotlin/org/solyton/solawi/bid/application/api/solawi-api.kt) and frontend Map readers are compatibility evidence, not disposable syntax.

```kotlin
// Features can declare their API fragments independently:
val authenticationApi = Api {
    post<Login, LoggedIn>(key = Login::class, url = "login")
}
val usersApi = Api {
    post<CreateUser, User>(key = CreateUser::class, url = "create")
}
val versionApi = Api {
    head<Unit, BuildInfo>(key = BuildInfo::class, url = "api/version")
}

// The application collects the contributions:
val applicationApi = Api {
    group("authentication", authenticationApi) // newly added overload
    module("users", usersApi)                   // already existed
    include(versionApi)                        // newly added
}
```

The only new public operations are `group(name, fragment: Api)` and `include(fragment: Api)`. Preserve existing `module(path, Api)` and the original `group(name) { ... }` and `module(path) { ... }`. No new builder paradigm, identity handle, HTTP enum, URL-normalization policy, immutable representation or collision policy is authorized by this task.

## Dependency direction

```text
independently declared Api fragments
               │
      Api.group / module / include
               │
   mathcat-api (Ktor-independent)
               ▲
               │ consumed by
   mathcat-ktorx (future Ktor route binder)
               │
             Ktor
```

`mathcat-api` has no Ktor dependency. MathCat Core `Reader` is used to adapt the original `EndPoint(key)` reader to the new core type. A portable `Parameters` data contract retains GET's input bound while old client codecs and serialization remain outside the new module; this is documented as a *portability seam*, not complete source/binary compatibility.

## What tests actually establish

- Original `Api` DSL, Map lookup and all methods still work.
- The assembled API has the **same** `EndPoint` entries as the equivalent inline declaration.
- `group(name, fragment)` has the same effect as inline `group`.
- `module(path, fragment)` retains the original path and group behavior; imported source fragment is unchanged.
- `include(fragment)` retains the supplied URLs and group metadata.
- Historical duplicate-key overwrite semantics are not silently changed.

The corrected first slice passed all four jobs of [CI run 44377915481](https://github.com/evoleq/mathcat/actions/runs/44377915481) on the Draft PR, **not yet merged**. Tests do not establish legacy client codec compatibility or a working Ktor route interpreter.

## Later work, only when separately undertaken

The Ktor-specific route binder belongs to **`mathcat-ktorx`**. It should map a route to a declared `KClass` endpoint, use the existing `method`/`url`/`group` fields and supply `Base(call, environment)` per request. If distinguishing the precise source module requires extra provenance, investigate how to add it **without redesigning `Api`**. See [Ktor adapter research](KTOR-API-COMPOSITION.md). No Solyton change.
