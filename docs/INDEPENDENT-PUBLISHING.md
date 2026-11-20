# Independent publishing identity — Evoleq ToolKit

**Status: multi-platform Maven Central staging configured; live publishing requires verified namespace, a real license and maintainer-controlled deployment.**

The artifact identity must not depend on GitHub. Our desired Maven Central namespace is `org.evoleq.toolkit` (requires verification of control of a suitable parent namespace such as `org.evoleq`); the target group is decoupled from `evoleq/toolkit` as a GitHub repository and from the `evoleq.toolkit.*` Kotlin package root.

Intended consumer usage:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("org.evoleq.toolkit:toolkit-core:<version>")
    implementation("org.evoleq.toolkit:toolkit-api:<version>")
    implementation("org.evoleq.toolkit:toolkit-routing:<version>")
    implementation("org.evoleq.toolkit:toolkit-evolution:<version>")
}
```

Maven Central verifies namespace authority. Before any publication, register the namespace through the Central Portal, configure legal POM/project metadata, supply artifact sources and documentation, signing and credentials, implement reproducible publication automation, and verify independent consumer resolution and the compatibility matrix.

JitPack smoke tasks still use legacy `com.github.evoleq.mathcat:toolkit-*:...` coordinates, but the repository has been renamed to `evoleq/toolkit`; verify actual remote JitPack resolution before claiming backward compatibility. Do not confuse either path with Maven Central.

The GitHub repository was renamed to `evoleq/toolkit`. That rename does not itself register any Sonatype Central namespace.

Artifact ID shortening (e.g. `toolkit-core` -> `core`) remains a possible **later** breaking publication change. Current staging preserves the `toolkit-*` artifact IDs; KMP target-specific publications are version-coupled.

Historic tags and published artifacts are immutable; verify dependency compatibility and avoid republishing old coordinates.
