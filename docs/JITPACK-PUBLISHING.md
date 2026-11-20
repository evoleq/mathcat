# Experimental modular publishing through JitPack

ToolKit 2.0 remains experimental (0.x). GitHub CI/annotated SemVer tags
are the **source-release gate**. JitPack builds tagged sources **on demand**;
creating a GitHub release does not prove any Maven artifact was built.

## External consumers

In `settings.gradle.kts`, add the JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Then use a **real tag with a successful JitPack build** (illustrative):

```kotlin
implementation("com.github.evoleq.mathcat:toolkit-core:v0.2.0")
implementation("com.github.evoleq.mathcat:toolkit-actions:v0.2.0")
```

The publishable modules are `toolkit-type-algebra`, `toolkit-core`, `toolkit-store`, `toolkit-actions`, `toolkit-api`, `toolkit-result`, `toolkit-serialization`, `toolkit-ktorx`, `toolkit-context`, `toolkit-transform` and `toolkit-serialization-ksp`. The `toolkit-serialization-ksp-fixture` is test-only and is **not** published.
Kotlin Multiplatform publishes root and target-specific Maven modules;
the root publication selects a suitable platform variant. Gradle
transitively resolves internal dependencies, including `toolkit-actions`
to `toolkit-store` and `toolkit-core`.

## Release coordinates and environment

`JITPACK=true` selects the group `com.github.evoleq.mathcat` for **all**
modules and `VERSION` selects the Maven version. JitPack supplies both
variables; Gradle preserves the tag's `v` prefix. Local development builds
keep `org.evoleq` and `0.0.0-SNAPSHOT` by default.

`jitpack.yml` pins JDK 17 and uses SDKMAN to install Gradle 9.2.1
(the version already used in CI) before running `publishToMavenLocal`.
A Gradle Wrapper jar is **not** currently committed; add the official,
validated wrapper separately, then switch the JitPack install command to
`./gradlew publishToMavenLocal`. Do not substitute an improvised wrapper.

Kotlin 2.3.20 root-publication currently needs a `kotlin-stdlib` metadata
artifact with the `all` classifier that the CI run could not fetch from
Maven Central. In JitPack/smoke builds only, `settings.gradle.kts` therefore
resolves **just `org.jetbrains.kotlin:kotlin-stdlib`** from JetBrains'
versioned development repository. Reassess and remove this exception once
upstream dependency resolution works without it. Stable builds should not
depend indefinitely on a development repository.

No credentials, signing, webhook or Maven Central publication is configured.
The final public license decision is still open.

## Smoke verification

GitHub CI publishes KMP root/JVM publications and standalone JVM-only modules into Maven Local under
JitPack coordinates. It parses their POM dependencies for correct module
group/versions, then compiles a **separate Gradle consumer** in
`publishing-smoke/` using Maven Local, not `project(...)` dependencies.

This verifies JVM artifact resolution. It does **not** yet verify all Android,
Apple, Windows, Linux, JS or Wasm Maven publications or the remote JitPack
service. Kotlin/Native cross-host publishing may require additional setup.
Check the real JitPack build log and a fresh remote consumer before
advertising any platform as released.

## Reproduce JVM publication smoke check

With JDK 17 and Gradle 9.2.1 installed:

```bash
JITPACK=true VERSION=v0.0.0-smoke gradle --no-daemon publishToMavenLocal
python3 scripts/verify_published_jvm.py v0.0.0-smoke
gradle --no-daemon -p publishing-smoke compileKotlin
```

## Integrated module graph (2026-11-19)

The compatibility manifest covers each Gradle module. The `publication` field
is `kmp` (root and JVM artifacts), `jvm` (standalone JVM artifact), or `none`
(test-only fixture excluded from publishing). The CI smoke test checks all
publishable JVM artifacts and an independent external consumer.
See `docs/INTEGRATION-2026-11-19.md` and issue #17 for remaining DX work.
