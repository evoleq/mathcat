# mathcat-api — the existing Evoleq API DSL, made modular

**Goal: minimal change to the author's DSL.** The implementation is adapted from the existing [Evoleq `Api` declaration](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/ktorx/api/api.kt) and its [original tests](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonTest/kotlin/org/evoleq/ktorx/api/ApiTest.kt). This is not a redesigned endpoint framework.

## The DSL stays the same

```kotlin
val api = Api {
    group("authentication") {
        post<Login, LoggedIn>(key = Login::class, url = "login")
    }
    module("users") {
        post<CreateUser, User>(key = CreateUser::class, url = "create")
    }
}
val endpoint: EndPoint<*, *>? = api[Login::class]
```

The existing `Api` data class delegates `Map<KClass<*>, EndPoint<*,*>>` to `endPoints`. All existing `head/get/post/put/patch/delete` overloads are retained (explicit `key` and reified-key variants). `EndPoint` remains the original sealed hierarchy with `url`, `requestType`, `responseType`, `group`, `parameters` and `method`. The `EndPoint(key)` Reader is retained, implemented using MathCat Core's nominal `Reader`.

**No rewrite:** URLs are not newly normalized, groups are not changed into path prefixes, the Map is not replaced by immutable descriptor lists, and the original key-overwrite behavior is unchanged. These may be evaluated independently later; they were not part of the user's request.

## Only the modularity is extended

The existing `module(path, fragment: Api)` already makes it possible to import one feature's API definition. Two small additions cover the remaining common cases:

```kotlin
// These fragments may live in independent Kotlin modules:
val authenticationApi = Api {
    post<Login, LoggedIn>(Login::class, "login")
}
val usersApi = Api {
    post<CreateUser, User>(CreateUser::class, "create")
}
val versionApi = Api {
    get<BuildInfoRequest, BuildInfo>(BuildInfoRequest::class, "api/version")
}

// Application assembles existing declarations, no Ktor dependency:
val applicationApi = Api {
    group("authentication", authenticationApi) // new overload
    module("users", usersApi)                   // existed already
    include(versionApi)                        // new, no prefix/group
}
```

Both additions use the **same copy / map overwrite semantics** as the original DSL. `include` preserves each copied endpoint's URL and group. `group(name, fragment)` behaves like the existing `group(name) { ... }`.

## Minimal portability seam

The original `Api.kt` imports the old `org.evoleq.math.Reader` and `org.evoleq.ktorx.client.Parameters`. The new module uses `mathcat-core`'s nominal `Reader` with the same caller-facing `EndPoint(key)` operation. It includes the **value-level** abstract `Parameters`, `EmptyParams` and `QueryParams` shape so GET constraints remain in place **without Ktor**. The old client-specific query encoder and kotlinx.serialization annotations are **not copied into this pure module**: that transport/serialization integration is separate work for `mathcat-ktorx`, not a change to existing Solyton code. Consequently this module is not a binary/source drop-in for every existing client import.

## Tests and next step

`commonTest` contains regression tests for the original DSL and Map behavior, all six HTTP methods, explicit/reified keys, `get` parameters, nested groups/modules and the old Reader. New tests compare the assembled API to the inline DSL, check importing without a prefix, and preserve key-overwrite behavior.

**Only after this is reviewed** should `mathcat-ktorx` bind endpoint descriptors to Ktor Routes. Nothing about the Ktor-specific adapter needs to reshape this DSL.
