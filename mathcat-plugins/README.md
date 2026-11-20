# MathCat Plugins (Kotlin Multiplatform)

Small, law-oriented runtime plugin foundation. **One Gradle/Maven module**,
with common contracts and platform-specific loaders in their own source sets.

## Functional composition

```kotlin
import org.evoleq.mathcat.plugins.*
import org.evoleq.mathcat.plugins.js.* // JS only

val host = PluginHostContext(
    hostApi = 1,
    capabilities = setOf("navigation"),
    properties = mapOf("containerId" to "plugin-slot")
)

// JS: manifest is retrieved fresh when this pipeline runs.
val plugins = Discover(
    HttpPluginCatalog("/plugins/plugin-manifest.json"),
    PluginTarget.JS, host
) * Activate(JsModuleLoader(), PluginTarget.JS, host)

val deployed: PluginResult<PluginDeployment> =
    plugins.run(setOf(PluginId("shares")))
```

`PluginResult<X> = Sum2<PluginFailure, X>` is a checked *sum*.
`PluginStep<A,B> = A -> suspend(PluginFailure + B)` is its effectful
**Kleisli arrow**. Composition is left-to-right `*` and short circuits on
the left summand. `Plan(manifest, selected, target, host)` is entirely pure:
it checks exact dependency versions, host protocol, capabilities, target
availability, duplicate IDs, missing dependencies and dependency cycles, and
returns a deterministic dependency-first ordering.

The runner activates dependencies first. `deployment.stop()` disposes
sessions then unloads handles, both in reverse dependency order. If an
activation fails it attempts reverse-order cleanup. This is *best effort*,
not transactional rollback, atomic update or security isolation.

## Manifest schema

```json
{
  "plugins": [
    {
      "id": "shares",
      "version": {"major": 1, "minor": 0, "patch": 0},
      "hostApi": 1,
      "requiredCapabilities": ["navigation"],
      "dependencies": [],
      "artifacts": {
        "JS": {"uri": "/plugins/shares.a3b1c2d3.js", "sha256": null}
      }
    }
  ]
}
```

The schema is round-tripped on all supported targets using Kotlin
serialization (`ParseManifest(json)` and `manifest.toJson()`).
`HttpPluginCatalog` revalidates the manifest using `fetch(...,
{cache:'no-cache'})`; the module filename itself should be content-hashed (the existing Solyton Nginx immutable-cache rule covers hashed *.js and *.css, not *.mjs)
and served with `Cache-Control: public, max-age=31536000, immutable`.
Keep the old hashed file available for existing browser sessions until a
controlled reload/upgrade.

## Browser ES-module ABI

The imported ES module is deliberately **plain JavaScript**, not an internal
Kotlin compiler binary ABI:

```js
export const id = "shares";
export const version = "1.0.0";

export async function activate(host) {
    const root = document.getElementById(host.properties.containerId);
    const element = document.createElement("div");
    element.textContent = "Loaded shares plugin";
    root.appendChild(element);

    return {
        dispose() { element.remove(); }
    };
}
```

The host passes a plain object `{hostApi,capabilities,properties}`.
Only declared capability **names** are exposed in v1; callable dispatch,
routing, storage and auth bridges need an explicit, typed, permissioned
host-to-plugin protocol in a later slice. A plugin can still mount a
self-contained UI on an agreed DOM element today.

Kotlin/JS can compile plugin implementations to JavaScript, but use an
explicit exported JS ABI. Do not assume arbitrary separately built Kotlin
objects are wire- or binary-compatible.

Dynamic ES modules cannot be unloaded from the browser's module registry:
`dispose` removes UI and subscriptions only. `sha256` verification is
**not implemented** by the JS adapter; it rejects a declared SHA-256 rather
than implying authenticity. Content hashes in filenames are for cache
invalidation, not cryptographic verification. Use trusted origins and
normal CSP/SRI policies where feasible.

## JVM JAR ABI

Build the JAR against the same host API and implement
`org.evoleq.mathcat.plugins.jvm.JvmPluginEntrypoint`:

```kotlin
class SharesPlugin : JvmPluginEntrypoint {
    override val id = "shares"
    override val version = "1.0.0"
    override suspend fun activate(host: PluginHostContext): PluginSession =
        PluginSession { /* stop background work */ }
}
```

Include this text file in the JAR:

```text
META-INF/services/org.evoleq.mathcat.plugins.jvm.JvmPluginEntrypoint
```

Its content is the fully qualified implementation class name. Provide a
**local** JAR pathname or `file:` URI and the JAR's real lowercase SHA-256
digest under `artifacts.JVM`. `JarPluginLoader` checks the checksum, uses
`ServiceLoader`, validates the provider's ID/version and closes its
`URLClassLoader` on stop. This is classloader-based extensibility, **not
isolation of untrusted code**. Downloading JARs, sandboxing and rolling
upgrades are separate concerns.

No implementation of JVM executable loading is claimed for Android, Native
or Wasm. Those targets compile and exercise the **shared planner**.

## Tests as executable documentation

`PluginAlgebraTest`: manifest roundtrip, deterministic dependency order,
version/capability errors, cycle detection, short-circuiting, successful
ordered activation, reverse disposal and failed-activation cleanup.

`JarPluginLoaderTest`: real temporary JAR with a `META-INF/services`
record, SHA-256 acceptance/rejection and successful service activation.

`JsModuleLoaderTest`: actual Node ES-module `import()` from a data URL
with JS identity/version handshake and activation/disposal.
