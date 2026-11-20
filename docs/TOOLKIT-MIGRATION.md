# From MathCat to Evoleq ToolKit

This repository's public library identity is **Evoleq ToolKit**. The repository has since been renamed to `evoleq/toolkit`. Historical `evoleq/mathcat` references are migration history, not the active source location.

## What changed

- Gradle root project: `mathcat` → `toolkit`.
- Modules are renamed from `mathcat-*` to `toolkit-*`.
- Kotlin packages move from `org.evoleq.mathcat.*` to `evoleq.toolkit.*`, in all production and test sources.
- Android library namespaces, imports, code examples and the independent publishing smoke consumer follow the new packages.
- KSP processor/provider is now `evoleq.toolkit.serialization.ksp.ToolkitSerializersProcessorProvider`; generated registry defaults to `evoleq.toolkit.serialization.generated`.
- KSP options are now `toolkit.serializers.package` and `toolkit.serializers.name`.
- Compatibility manifest, release tests and CI publish/compile tasks use the new Gradle module IDs.

The complete current module map is:

| Before | After |
|---|---|
| mathcat-type-algebra | toolkit-type-algebra |
| mathcat-core | toolkit-core |
| mathcat-store | toolkit-store |
| mathcat-actions | toolkit-actions |
| mathcat-api | toolkit-api |
| mathcat-result | toolkit-result |
| mathcat-serialization | toolkit-serialization |
| mathcat-ktorx | toolkit-ktorx |
| mathcat-middleware | toolkit-middleware |
| mathcat-plugins | toolkit-plugins |
| mathcat-plugins-actions | toolkit-plugins-actions |
| mathcat-context | toolkit-context |
| mathcat-transform | toolkit-transform |
| mathcat-serialization-ksp | toolkit-serialization-ksp |
| mathcat-serialization-ksp-fixture | toolkit-serialization-ksp-fixture |
| mathcat-routing | toolkit-routing |
| mathcat-evolution | toolkit-evolution |
| mathcat-evolution-runtime | toolkit-evolution-runtime |

## Updating consumers

Gradle module paths and Kotlin imports change together:

```kotlin
// Gradle composite build / in-repository consumer
implementation(project(":toolkit-core"))
implementation(project(":toolkit-api"))
```

```kotlin
import evoleq.toolkit.core.optics.*
import evoleq.toolkit.api.*
```

### Publishing and repository coordinates

For historical compatibility, the existing Gradle JitPack smoke build still uses `com.github.evoleq.mathcat`. The repository is now `evoleq/toolkit`; remote resolution of these legacy coordinates must be verified before advertising them. An illustrative old coordinate:

```kotlin
implementation("com.github.evoleq.mathcat:toolkit-core:<tag>")
```

This is **not** a published release guarantee. JitPack's repository-derived coordinates must not be silently changed without an independent consumer test. Maven Central staging uses `org.evoleq.toolkit:toolkit-*:<version>` under separate release gates.

The ordinary non-publication Gradle group remains `org.evoleq`; the opt-in Maven Central group is `org.evoleq.toolkit`. Group and Kotlin package are distinct concepts. Historic published MathCat artifacts and old Git tags are not modified or republished.

### Compatibility

This is a **source and binary breaking namespace change**. There are no old-package forwarding aliases in this branch; users must update imports and dependencies together. The release compatibility record continues to describe same-release dependencies between modules. That record is not an ABI or wire compatibility proof.

## Validation before merging

Run:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/release.py validate
gradle :toolkit-core:jvmTest :toolkit-api:jvmTest :toolkit-serialization-ksp-fixture:test
```

The cross-platform and Maven-local publication tests are specified in `.github/workflows/ci.yml` and must pass before merging. The routing, pure Flow and runtime slices are already incorporated into `master`; current CI checks all 18 modules.

## Branding

**Evoleq ToolKit — The Art of Composition.** The Stokes cat remains the mascot. The original illustration is preserved byte-for-byte in `assets/toolkit-original-stokes.png`.
