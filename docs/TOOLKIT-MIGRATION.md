# From MathCat to Evoleq ToolKit

This repository's public library identity is **Evoleq ToolKit**. The repository slug `evoleq/mathcat` remains unchanged during this migration; the distinction is intentional.

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

As long as the GitHub repository remains `evoleq/mathcat`, the **JitPack group stays** `com.github.evoleq.mathcat`. For example, after a new tag builds successfully:

```kotlin
implementation("com.github.evoleq.mathcat:toolkit-core:<tag>")
```

This is **not** a published release guarantee. Repository-derived JitPack coordinates cannot be silently switched to `com.github.evoleq.toolkit` by changing `rootProject.name`. A future GitHub repository rename would require a separate release/consumer migration with explicit compatibility handling.

The non-JitPack Gradle group remains `org.evoleq`; group and Kotlin package are distinct concepts. Historic published MathCat artifacts and old Git tags are not modified or republished.

### Compatibility

This is a **source and binary breaking namespace change**. There are no old-package forwarding aliases in this branch; users must update imports and dependencies together. The release compatibility record continues to describe same-release dependencies between modules. That record is not an ABI or wire compatibility proof.

## Validation before merging

Run:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/release.py validate
gradle :toolkit-core:jvmTest :toolkit-api:jvmTest :toolkit-serialization-ksp-fixture:test
```

The cross-platform and Maven-local publication tests are specified in `.github/workflows/ci.yml` and must pass before merging. An open routing/flow PR built against `mathcat-*` may need rebasing onto this change.

## Branding

**Evoleq ToolKit — The Art of Composition.** The Stokes cat remains the mascot. The original illustration is preserved byte-for-byte in `assets/toolkit-original-stokes.png`.
