# ToolKit Plugins (Kotlin Multiplatform)

Small, law-oriented runtime plugin foundation. **One Gradle/Maven module**,
with common contracts and platform-specific loaders in their own source sets.

## Functional composition

```kotlin
import evoleq.toolkit.plugins.*
import evoleq.toolkit.plugins.js.* // JS only

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

## On-demand access: Prism match/build + Kleisli loading

The common `toolkit-core` Prism provides **pure** `match` and `build`.
`LoadedPlugin: Prism<PluginSlot, PluginSlot.Loaded>` selects the loaded
case of the sum `Missing + Loaded`. On a miss, we interpret a new effect:

```kotlin
var slot: PluginSlot = PluginSlot.Missing
val access = MatchPlugin() * LoadIfMissing(
    selection = setOf(PluginId("shares")),
    catalog = catalog,
    loader = loader,
    target = PluginTarget.JS,
    host = host
)

// run only when the feature is requested:
when (val result = access.run(slot)) {
    is Sum2.Left -> report(result.value)
    is Sum2.Right -> slot = result.value
}
```

On a hit, the stored deployment is returned **without reloading or
fetching the manifest**. On a miss, the Kleisli stage performs
`Discover * Activate` and then injects the activated result through
`LoadedPlugin.build(...)`. The slot represents **one explicit selection
set**: a different selection yields `SlotSelectionMismatch` rather than
silently discarding live resources. The host owns and serializes slot updates,
must dispose the deployment on shutdown, and must not assume atomic
multi-plugin upgrades or concurrent access.

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
Optional **host-owned, scoped** callbacks are now available: construct
`JsModuleLoader(callbacks = bridge, callbackScope = scope)` with a
`CoroutineScope` belonging to the host. If the manifest requests and the
host supports `actions` or `navigation`, the plugin receives a plain JS
`host.actions.dispatch(key): Promise<boolean>` and/or
`host.navigation.navigate(key): Promise<boolean>`. Each invocation is
checked against host-side, plugin-specific grants, not merely the manifest.

The independent `toolkit-plugins-actions` MPP module provides
`ActionNavigationBridge`, `BindAction`, `BindEnvelope`,
`PluginRoute` and `PluginGrant`. The adapter holds the typed ToolKit
Actions and maps pre-approved navigation keys to host-controlled paths.
The plugin never receives `Storage<Application>` or raw
`ActionDispatcher<Application>`. Runtime action permissions and route
permissions are enforced at the host adapter; this is not a replacement for
authentication or server-side authorization.

Kotlin/JS can compile plugin implementations to JavaScript, but use an
explicit exported JS ABI. Do not assume arbitrary separately built Kotlin
objects are wire- or binary-compatible.

Dynamic ES modules cannot be unloaded from the browser's module registry:
`dispose` removes UI and subscriptions only. `sha256` verification is
**not implemented** by the JS adapter; it rejects a declared SHA-256 rather
than implying authenticity. Content hashes in filenames are for cache
invalidation, not cryptographic verification. Use trusted origins and
normal CSP/SRI policies where feasible.

### Plugin-side JavaScript commands

```js
export const id = "shares";
export const version = "1.0.0";

export async function activate(host) {
  if (host.actions) {
    const dispatched = await host.actions.dispatch("shares.refresh");
    if (!dispatched) throw Error("Host denied the action");
  }
  if (host.navigation) await host.navigation.navigate("shares.home");
  return { dispose() {} };
}
```

Commands are revoked once the plugin session is disposed or its handle is unloaded:
retaining a stale JavaScript callback must not keep host capabilities live.

The **host** must bind `shares.refresh` to an actual typed `Action` and
`shares.home` to an internal route; unrecognized or ungranted names are
rejected. Dispatch acknowledgement is not action completion.

## JVM JAR ABI

Build the JAR against the same host API and implement
`evoleq.toolkit.plugins.jvm.JvmPluginEntrypoint`:

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
META-INF/services/evoleq.toolkit.plugins.jvm.JvmPluginEntrypoint
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
with JS identity/version handshake and activation/disposal, including
optional asynchronously authorized Action and navigation calls.
