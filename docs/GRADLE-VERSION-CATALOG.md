# Gradle Version Catalog

ToolKit keeps **external** Gradle plugin and library versions in
`gradle/libs.versions.toml`. Gradle automatically exposes the `libs` aliases
in the main multi-module build.

An example (for the main Gradle build):

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
    }
}
```

## Separation from repository releases

**Do not place ToolKit's own artifact version in the catalog.**
Internal module dependencies remain explicit `project(":toolkit-core")`
references, cross-checked against `compatibility/modules.json` by
`scripts/release.py validate`. The repository-wide SemVer release planner
still controls source tags and compatibility records after successful CI.
See [Release Versioning](RELEASE-VERSIONING.md).

Changing a file under `gradle/` is package-relevant for the existing
release planner; this catalog is thus part of the release impact analysis.

## Deliberately independent publication smoke build

`publishing-smoke/` is its own independent Gradle consumer build with
published-module coordinates, not `project(...)` dependencies. Its Kotlin
plugin pin remains isolated to preserve the external-consumer smoke check.
When upgrading Kotlin in the main catalog, review the consumer version
at the same time. That copy is deliberate, not a second repository
release-version source.

CI runs Python regression checks for catalog references and then the
actual JVM/JS/Native/Android compilation and Maven-local publication smoke.
