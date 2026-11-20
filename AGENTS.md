# Evoleq ToolKit collaboration contract

The public library is **Evoleq ToolKit**. PR #28 established a breaking namespace migration that ALL contributors must follow.

- Gradle root: `toolkit`; modules: `toolkit-*`; packages, imports and Android namespaces: `evoleq.toolkit.*`.
- Desired future GitHub repository: `evoleq/toolkit`. The repository is now `evoleq/toolkit`; historical `evoleq/mathcat` URLs may redirect, but release identities must be checked explicitly.
- **Maven Central staging group:** `org.evoleq.toolkit` (subject to domain-based namespace verification). Current artifact IDs remain `toolkit-core`, `toolkit-api`, `toolkit-routing`, `toolkit-evolution`, `toolkit-evolution-runtime`, etc. Shortened IDs are a separate, unimplemented publishing contract.
- **Legacy JitPack group** retained in Gradle's smoke builds even after the repository rename: `com.github.evoleq.mathcat`. Do not assume this legacy group is the permanent public API. Do not assume old GitHub-slug-dependent coordinates resolve after the rename until a remote JitPack consumer verifies them.
- Do not independently change publication coordinates until registry access, metadata, signing, multi-platform validation, legacy consumer migration and version compatibility have been coordinated.
- Keep all modules reflected in `settings.gradle.kts`, `compatibility/modules.json`, CI matrix and published JVM smoke tests.
- Keep the same-release compatibility graph in sync with `project(":toolkit-*")` declarations and KSP build requirements.
- Rebase or safely re-integrate feature branches onto the newest `master`, updating sources and tests from `org.evoleq.mathcat.*` and `mathcat-*`. Avoid overwriting other contributors' branches.
- CI and runtime/boundary semantics must pass before merging. Avoid hidden effects or host scope ownership in pure Flow and routing primitives.
- Preserve Stokes branding and the mathematics.

See `docs/TOOLKIT-MIGRATION.md` and `docs/RELEASE-VERSIONING.md`.
