# Dynamically composed applications — a small MathCat plugin algebra

**Research implementation, November 2026.** No general plugin framework
or universal security system is claimed.

## 1. Separate three questions

1. **Description:** What is a plugin? Its immutable ID, version,
   host-protocol requirement, capabilities, exact dependencies and
   platform-specific artifact location.
2. **Composition:** Can a requested set of plugins be safely *planned*,
   including dependency order and incompatibility diagnostics?
3. **Interpretation:** Which platform adapter loads, activates, disposes and
   releases executable code? That is an effect, not a pure function.

This gives an explicit boundary:

```text
manifest snapshot -> Plan -> dependency-first PluginPlan
      (IO)            (pure)        |
                                  Activate
                                    (IO)
                                      |
                          PluginDeployment / failure
```

## 2. Visible categorical structure

`PluginResult<A> = PluginFailure + A` is a sum. The pure planner has type

```text
Plan : Manifest × Selected × Target × Host -> PluginFailure + PluginPlan
```

The effectful stages have the shape

```text
PluginStep<A,B> = A -> suspend(PluginFailure + B)
```

The `*` operator is Kleisli composition on the *checked* sum result.
It short-circuits on a failure, preserving successful values along a
dependency-checked activation path. No custom HKT typeclass hierarchy is
needed: `Sum2` and plain Kotlin types already reveal the algebra.

```kotlin
val pipeline = Discover(catalog, target, host) * Activate(loader, target, host)
val result = pipeline.run(setOf(PluginId("shares")))
```

`Discover` reads a fresh runtime manifest; `Plan` itself is a pure,
deterministic function. `Activate` runs sequentially in dependency order
and attempts reverse-order compensation after errors. Cancellation is not
turned into a domain failure.

## 3. A real Prism at the runtime boundary

The pure `Prism<S,A>` in `mathcat-core` exposes

```text
match : S -> A + S       (Left = matched)
build : A -> S
```

For a lawful sum-case Prism `match(build(a)) = Left(a)` and, when
`match(s) = Left(a)`, `build(a) = s`. These are tested in
`PrismTest` and `PluginAlgebraTest`.

`LoadedPlugin` selects `Loaded` from
`PluginSlot = Missing + Loaded`. The composition

```kotlin
MatchPlugin() * LoadIfMissing(selection, catalog, loader, target, host)
```

first **matches** a running deployment. Only on a miss does the
**effectful** second stage run `Discover * Activate`. It then uses
the **pure** `Prism.build` to re-embed the loaded value.

This prevents the category mistake of claiming the optic performs IO:
`match/build` are ordinary pure functions, while plugin downloading,
importing and activation are interpreted Kleisli arrows.

## 4. JVM and JS interpretations

- **JVM:** `JarPluginLoader` reads a local JAR, checks its SHA-256 and
  activates its `ServiceLoader`-registered `JvmPluginEntrypoint`.
  Closing the classloader is resource cleanup, not guaranteed garbage
  collection/unloading of Java classes, and is not an isolation sandbox.
- **JS:** `HttpPluginCatalog` fetches a revalidated JSON manifest,
  `JsModuleLoader` calls `import(/* webpackIgnore: true */ uri)`, then
  invokes `activate(host)` from a plain ES-module ABI. Browser ES modules
  are not generally unloadable; only their contributions can be disposed.
- **Android / Apple / Linux / Windows Native / Wasm:** shared algebra,
  manifests and planning are KMP. No JAR/classloader or JS dynamic-loader
  support is promised for these targets yet.

## 5. Cache-busting and deployment

Solyton already serves Webpack's `[contenthash]` JavaScript chunks with
long-lived immutable caching, while revalidating the SPA HTML. Keep that.

For independently deployed plugins introduce one *revalidated* JSON
manifest, which points to *immutable* content-hashed ES module URLs.
A new plugin release updates the manifest and uploads the new artifact,
**without replacing old URLs**. Fail closed on absent dependencies,
unsupported host API, missing capabilities or mismatched exact versions.

Do not confuse:

- cache identity (file content hash),
- cryptographic authenticity (signed/verified manifest and artifact),
- binary/runtime compatibility (host API version is just one necessary
  check),
- isolation (an ES module runs with its document's privileges),
- deployment atomicity (a sequence of effects is not a transaction).

The current JS adapter checks ID/version exports and uses normal trusted
origin/TLS policy, but does **not** verify signatures or SHA-256. It refuses
declared `sha256` fields instead of silently claiming integrity.

## 6. What it is useful for in Solyton

A feature such as Shares or Banking can be built independently and expose
the small exported JS contract. The host can discover and mount it without
a host rebuild, using `containerId` as a first DOM integration seam.
The host's full `Storage<Application>` and privileged action dispatcher
are **not** exposed. An opt-in `mathcat-plugins-actions` adapter binds
typed `Action<S,I,O>` values and fixed internal route paths to allowlisted
command keys, with a **separate grant set per plugin**.
`JsModuleLoader` can expose these through a plain JavaScript callback ABI
using a host-owned coroutine scope. Permissions are rechecked on each call,
independently of plugin manifest contents. This remains an explicit
plugin-to-host boundary and NOT an authorization sandbox.

What is deliberately deferred: plugin route registration and rendering,
dynamic action parameters/codecs, a complete authentication/authorization
policy and isolation model, remote JAR downloads, hot swapping without a
reload, version ranges and lockfiles, signed manifests, atomic upgrades,
cross-compiled Kotlin runtime ABI version negotiation, independent
JitPack releases and process orchestration.

## 7. Law and documentation tests

The plugin module tests the sum short-circuit law, deterministic
dependency-first ordering, dependency cycle/version/capability rejection,
activation and reverse cleanup, and actual JVM/JS load paths. They
document the guarantees that are implemented rather than extrapolating
runtime guarantees from an attractive algebraic notation.

This is a good book example precisely because the **same pure
mathematical structures** connect a static architecture description to
an executable application that can change composition at runtime.

## Manuscript connection

The book-facing explanation, with a running host-and-shares example and a
clear separation of Prism matching from effectful loading, is in
[`book/RUNTIME-COMPOSITION.md`](book/RUNTIME-COMPOSITION.md).
This implementation document remains the technical reference; the book draft
does not claim that the host integration has already shipped in Solyton.
