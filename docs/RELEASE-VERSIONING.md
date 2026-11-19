# Versioning and compatibility — initial implementation

MathCat currently uses **one repository SemVer version for all four published-module candidates**. Its module dependencies are declared by Gradle projects and mirrored in [compatibility/modules.json](../compatibility/modules.json). Independent module versions, a cross-repository compatibility resolver, artifact publication, API/ABI checking and wire-compatibility claims are **not implemented** here.

## Local use

~~~bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/release.py validate
python3 scripts/release.py plan --output build/release-plan.json
gradle verifyReleaseCompatibility
gradle planRelease
~~~

The two Gradle tasks are adapters to the same standard-library release planner. planRelease writes build/release/plan.json. No command above tags, publishes or modifies Git history.

Use -PreleaseVersion=0.2.0 (or RELEASE_VERSION=0.2.0) to set the common Gradle artifact version explicitly. Untagged developer builds default to 0.0.0-SNAPSHOT; the Gradle build does **not** silently infer an artifact version from unverified working-tree tags.

## CI release policy

The existing CI tests JVM, JS, Wasm, Linux and Windows native, and compiles Android and iOS targets (macOS tests also run). The SemVer workflow only proceeds after the **whole CI workflow** succeeds for the current master commit. It additionally reruns the release planner tests, validates the module manifest, and creates a plan.

- Changes under module src/*Main/, module build.gradle.kts, common build/Gradle configuration, or the compatibility manifest are package-relevant. Other changes (docs, tests, workflow files) do not by themselves create releases.
- A reachable vMAJOR.MINOR.PATCH tag is the base for the next version. feat: increments MINOR; BREAKING CHANGE:/feat!: increments MAJOR, or MINOR while 0.x; other package-relevant changes increment PATCH. A new repository starts at v0.1.0. Conventional Commit messages describe intent, **not a proof of compatibility**.
- Changed modules and the transitive reverse-dependency impact are recorded in the plan. In this first phase, affected modules are verified together, but all modules share one version on release.
- A successful build of a package-relevant change creates an annotated Git tag and a GitHub source release with a machine-readable compatibility record attached. The record contains the module-version mapping and the exact passing CI run. The evidence covers CI only, **not** a formal proof of source, binary, behavior or wire compatibility.
- Releases are never created for stale CI commits, and tags are never replaced. GitHub Releases and tagged source commits are separate from Maven publication; the latter needs additional publishing and signing configuration.

## Invariants implemented in the first phase

1. The manifest's module set matches Gradle's settings.gradle.kts module set.
2. Every declared internal module dependency matches the corresponding Gradle project(...) dependency, with no unknown modules or dependency cycles.
3. Internal dependencies in a release record resolve to the *same* repository release version.
4. A planned new tag is strictly above the highest reachable SemVer tag, and is only emitted for package-relevant changes.
5. Tagging runs only after a successful CI run against the exact current master commit.

The compatibility record is a **release snapshot**, not a computed compatibility matrix across historical versions. The validator currently recognizes standard api(project(...))/implementation(project(...)) declarations; unusual dynamic Gradle dependency declarations require extending the adapter.

## Next stages

Add published multiplatform artifacts and a BOM/version catalog; implement ABI baselines and serialization contract checks; define cross-repository compatibility and independently versioned modules only when release cycles diverge. Preserve checked evidence separately from declared compatibility constraints.
