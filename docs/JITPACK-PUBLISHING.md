# Experimental modular publishing through JitPack

MathCat 2.0 remains experimental (0.x). GitHub CI/annotated SemVer tags
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
implementation("com.github.evoleq.mathcat:mathcat-core:v0.2.0")
implementation("com.github.evoleq.mathcat:mathcat-actions:v0.2.0")
```

The other modules are `mathcat-store` and `mathcat-type-algebra`.
Kotlin Multiplatform publishes root and target-specific Maven modules;
the root publication selects a suitable platform variant. Gradle
transitively resolves internal dependencies, including `mathcat-actions`
to `mathcat-store` and `mathcat-core`.

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

No credentials, signing, webhook or Maven Central publication is configured.
The final public license decision is still open.

## Smoke verification

GitHub CI publishes all four root and JVM publications into Maven Local under
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
JITPACK=true VERSION=v0.0.0-smoke gradle --no-daemon \
  :mathcat-type-algebra:publishKotlinMultiplatformPublicationToMavenLocal \
  :mathcat-type-algebra:publishJvmPublicationToMavenLocal \
  :mathcat-core:publishKotlinMultiplatformPublicationToMavenLocal \
  :mathcat-core:publishJvmPublicationToMavenLocal \
  :mathcat-store:publishKotlinMultiplatformPublicationToMavenLocal \
  :mathcat-store:publishJvmPublicationToMavenLocal \
  :mathcat-actions:publishKotlinMultiplatformPublicationToMavenLocal \
  :mathcat-actions:publishJvmPublicationToMavenLocal
python3 scripts/verify_published_jvm.py v0.0.0-smoke
gradle --no-daemon -p publishing-smoke compileKotlin
```
