# Independent publishing identity — Evoleq ToolKit

**Status: target design, not published or activated.**

The artifact identity must not depend on GitHub. Our desired Maven Central namespace is `org.evoleq.toolkit` (requires verification of control of a suitable parent namespace such as `org.evoleq`); the target group is decoupled from `evoleq/toolkit` as a GitHub repository and from the `evoleq.toolkit.*` Kotlin package root.

Intended consumer usage:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("org.evoleq.toolkit:core:<version>")
    implementation("org.evoleq.toolkit:api:<version>")
    implementation("org.evoleq.toolkit:routing:<version>")
    implementation("org.evoleq.toolkit:evolution:<version>")
}
```

Maven Central verifies namespace authority. Before any publication, register the namespace through the Central Portal, configure legal POM/project metadata, supply artifact sources and documentation, signing and credentials, implement reproducible publication automation, and verify independent consumer resolution and the compatibility matrix.

The current build intentionally uses `com.github.evoleq.mathcat:toolkit-*:...` **only for JitPack smoke verification**, because the repository slug remains `evoleq/mathcat`. Do not confuse this with a new artifact release under `org.evoleq.toolkit`. An optional custom-domain JitPack mapping may provide a brand-aligned staging path before Central.

The GitHub repository may be renamed to `evoleq/toolkit` once the active feature stack is merged and collaborators' remotes and CI references are updated. A repository rename alone will not register a Maven Central namespace.

Artifact ID shortening (e.g. `toolkit-core` -> `core`) must be handled as a separate publication contract because Gradle project paths need not equal Maven artifact IDs, and KMP has target-specific publications.

Historic tags and published artifacts are immutable; verify dependency compatibility and avoid republishing old coordinates.
