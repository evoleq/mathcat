# MathCat — Multiplatform architecture and verification

**Date:** 2026-11-18 · **Status:** Draft (platform verification underway) · **PR:** [#2](https://github.com/evoleq/mathcat/pull/2)

MathCat's `mathcat-core` and `mathcat-type-algebra` modules deliberately share one supported-target declaration. All mathematical primitives are in `commonMain`; neither module includes Android, Apple, web browser, platform IO, Compose, or JVM-only runtime code.

## Target and CI matrix

| Platform | Gradle target(s) | CI host and task | Verification intent |
| --- | --- | --- | --- |
| JVM | `jvm` | Ubuntu: `jvmTest` | Compile + run common tests |
| JavaScript | `js(IR) { nodejs() }` | Ubuntu: `jsNodeTest` | Compile + run common tests |
| WebAssembly | `wasmJs { nodejs() }` | Ubuntu: `wasmJsNodeTest` | Compile + run common tests |
| Android | new `com.android.kotlin.multiplatform.library` target `android` | Ubuntu + Android SDK: `compileKotlinAndroid` | **Compile only**; no device tests claimed |
| Linux Native | `linuxX64` | Ubuntu: `linuxX64Test` | Compile + run common tests |
| Windows Native | `mingwX64` | Windows: `mingwX64Test` | Compile + run common tests |
| iOS | `iosArm64`, `iosX64`, `iosSimulatorArm64` | macOS: `compileKotlinIosArm64` and `compileKotlinIosSimulatorArm64` | Compile two variants; simulator/device execution **not** claimed |
| macOS Native | `macosX64`, `macosArm64` | macOS: `macosX64Test` | Run x64 tests; arm64 declared but not tested in CI |

**Status is not a promise of passing CI.** The [expanded CI run](https://github.com/evoleq/mathcat/actions) is the authority for pass/fail. The package-only JVM/JS baseline [passed](https://github.com/evoleq/mathcat/actions/runs/44270455594). Android, Apple, Linux Native, Windows and Wasm results must be independently checked after workflow changes.

## Toolchain and compatibility

- Kotlin Gradle plugin: **2.3.20** (was 2.1.0 in the first spike).
- Android Gradle plugin: **9.0.0**, applying the **purpose-built** `com.android.kotlin.multiplatform.library` plugin, *not* the deprecated Android target integration via `com.android.library`.
- Gradle: **9.2.1** in CI, Java **17**.
- Android: `compileSdk = 36`, `minSdk = 24`. SDK packages installed by the Ubuntu CI runner.
- All modules use `kotlin("test")` in `commonTest`. Individual platform jobs decide whether the tests **run** or only compile.

Kotlin's official compatibility chart supports this chosen combination of Kotlin 2.3.20, AGP 9.0 and Gradle 9.2.1. Sources: [Kotlin KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin), [AGP 9.0 compatibility](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [Native target hosts](https://kotlinlang.org/docs/native-target-support.html).

## Deliberate boundaries

1. **No duplicate platform implementations.** Identical laws and pure algorithms remain in `commonMain`. Platform-only adapters can be introduced later in target-specific source sets.
2. **Android is Android, not merely JVM.** A passing `jvmTest` does not prove Android AAR compilation or Android runtime behavior.
3. **iOS requires Apple tooling for Apple CI.** We compile selected iOS targets on macOS and do not imply device/simulator tests without running them.
4. **Native memory, JS naming and Wasm support** can expose portability problems that JVM alone misses. CI runs concrete target tasks.
5. **Effects remain separate.** Storage, Android UI, iOS keychain, Ktor engines and native interop belong in adapters/effects modules, not in the algebraic core.
6. **Publishing is deferred.** No Maven Central/JitPack/GitHub Packages choice, signing, or release credentials are part of this PR.

## Change log

- 2026-11-18: Original Kotlin 2.1.0 / Gradle 8.6 JVM+JS baseline built and tested.
- 2026-11-18: Core packages separated into `core.state`, `core.reader`, `core.writer`, `core.optics`, `core.collections`; baseline JVM+JS retest [passed](https://github.com/evoleq/mathcat/actions/runs/44270455594).
- 2026-11-18: Toolchain updated to Kotlin 2.3.20, Gradle 9.2.1, AGP 9.0.0; expanded jobs for JVM, JS, Wasm, Android, Linux, Windows, Apple targets. **CI verification is ongoing**, including external SDK/toolchain constraints.
