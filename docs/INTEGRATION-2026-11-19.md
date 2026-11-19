# Consolidation of MathCat API, Result, serialization, Ktor and publishing

This branch integrates original, stacked feature PRs #8–#14, on top of the already
merged SemVer/compatibility work #15, with JitPack setup from #16.
Research designs from #7 are included as **research**, not as frozen APIs.

## Canonical module ownership

- `mathcat-api` — original `Api` / `EndPoint` DSL, modular API composition (#8).
- `mathcat-result` — portable Result algebra and exception policies (#10).
- `mathcat-serialization` — feature-composable registry + legacy Result JSON (#9/#12).
- `mathcat-serialization-ksp` — optional KSP registration generator (#9).
- `mathcat-serialization-ksp-fixture` — test-only consumer (not published).
- `mathcat-ktorx` — Ktor client/server binding (#11), Solyton-style KtorIO
  and typed Result client (#14).
- `mathcat-context` — canonical server Contextual (JVM-only, Ktor-free, #13).
- `mathcat-transform` — pure Result and Contextual transformations
  (JVM-only, Ktor-free, #13). KtorIO delegates its pure steps to this module.
- `mathcat-ktorx.Contextual` stays a source alias to `mathcat-context.Contextual`.
  Client Contextual is intentionally distinct (no authenticated user UUID).

## Architecture and deferred design

- No rewrite of the pre-existing `Api` DSL. Ktor depends on the pure API,
  not the other way round.
- `bindEndpoint` creates `Base(call, environment)` **per request**.
  Dependencies and authentication are injected, never global mutable state.
- Boundary / Domain / Environment are architectural roles, not yet a required
  public hierarchy of classes. More ergonomic route registration, explicit
  Boundary injection, diagnostics and DX examples are deferred to issue #17.
- Pure transformations remain available independently of Ktor.
- Reassess historical HTTP failure-envelope compatibility before app migration.

## Verification before merge to master

Verify portable + Android + Apple + Windows tests; JVM KSP generated fixture;
Ktor testApplication; standalone context and transform tests; release manifest
validation; and all published root/JVM artifacts plus separate Maven consumer.
Only successful CI should permit merging this integration branch. Merging to
master can trigger automated source tagging; Maven Central is not configured.
