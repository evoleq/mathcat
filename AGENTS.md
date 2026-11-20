# Evoleq ToolKit collaboration contract

The public library is **Evoleq ToolKit**. PR #28 established a breaking namespace migration that ALL contributors must follow.

- Gradle root: `toolkit`; modules: `toolkit-*`; packages, imports and Android namespaces: `evoleq.toolkit.*`.
- Desired future GitHub repository: `evoleq/toolkit`. The current repository `evoleq/mathcat` is a transition name, not a library namespace.
- **Target independent Maven Central group:** `org.evoleq.toolkit` (subject to domain-based namespace verification). Target short artifact IDs: `core`, `api`, `routing`, `evolution`, `evolution-runtime`, etc. These are intended end-user coordinates and NOT claimed as published.
- **Temporary JitPack group** while repository slug is still `evoleq/mathcat`: `com.github.evoleq.mathcat`. Do not assume this legacy group is the permanent public API. JitPack can also map a controlled custom domain using DNS TXT; verify before switching.
- Do not independently change publication coordinates until registry access, metadata, signing, multi-platform validation, legacy consumer migration and version compatibility have been coordinated.
- Keep all modules reflected in `settings.gradle.kts`, `compatibility/modules.json`, CI matrix and published JVM smoke tests.
- Keep the same-release compatibility graph in sync with `project(":toolkit-*")` declarations and KSP build requirements.
- Rebase feature branches onto the newest `master`, updating sources and tests from `org.evoleq.mathcat.*` and `mathcat-*`. Avoid overwriting other contributors' branches.
- CI and runtime/boundary semantics must pass before merging. Avoid hidden effects or host scope ownership in pure Flow and routing primitives.
- Preserve Stokes branding and the mathematics.

See `docs/TOOLKIT-MIGRATION.md` and `docs/RELEASE-VERSIONING.md`.
