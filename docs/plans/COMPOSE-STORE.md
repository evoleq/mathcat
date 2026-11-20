# Stable @Composable Store — implementation plan

**2026-11-20 · Plan, not an implementation.**  
**Proposed artifact:** optional `mathcat-compose` MPP module.  
**Fundamental decision:** the **automaton surrounds the Store**; the Store does not start, own or manage it.

## What MathCat already has

- `mathcat-core`: `Storage<S>` with synchronous `read/write`, `Storage * Lens`.
- `mathcat-store`: `Store<S,D>` as a pairing of existing storage and dispatcher capabilities, without new Memory allocation or coroutines.
- `mathcat-actions`: `Store<S, ActionDispatcher<S>> * Lens<S,A>` for consistent state and action projection.

The [legacy Evoleq `@Composable Store`](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/jsMain/kotlin/org/evoleq/compose/storage/store.kt) is already a concise receiver DSL. Keep that ergonomic surface. Its implementation must **not** grow an implicit action-processor/automaton.

In [Solyton's existing `Storage<Application>`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/storage.kt), `application` and `modals` are Compose-observable while `context` and `processes` are plain values. An exclusive `when` write router may drop simultaneous updates. The existing [onDispatch effect](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/event/dispatch.kt) uses a detached `CoroutineScope(Job())`. These observations motivate tests, not a claim that a runtime bug has already been reproduced.

## Architecture

```text
             ApplicationFlow       <- owns automaton, events, cancellation
                    |
                    | uses / observes
                    v
            Store<S, D>             <- Storage<S> + dispatcher D
                    |
                    | optional adapter
                    v
            mathcat-compose         <- snapshot observation, receiver UI
                    |
                    v
               Compose UI
```

The Flow, **not** the Store, owns the router subautomaton and the one action processor. A Store is safe to compose/focus without accidentally creating coroutines.

## Minimal proposed API (subject to compiling client tests)

```kotlin
// Optional observable Storage factory. No background execution.
@Composable
fun <S> rememberSnapshotStorage(
    ownerKey: Any? = Unit,
    initial: () -> S
): Storage<S>

// Minimal receiver DSL for an already-owned Store; does not own Store lifetime.
@Composable
fun <S, D> Store(
    store: Store<S, D>,
    content: @Composable Store<S, D>.() -> Unit
)

// ApplicationFlow (external) supplies lifecycle to the UI:
@Composable
fun <S, D> ApplicationView(
    runtime: ApplicationRuntime<S, D>,
    content: @Composable Store<S, D>.() -> Unit
)
```

These names/types are **illustrative only**. Finalize after a working Compose HTML fixture; do not create an adapter solely to reproduce familiar DSL labels.

## Composition and tests

- **State identity:** a borrowed Storage/Store is never replaced or disposed during ordinary recomposition. An owned snapshot storage factory creates once per explicit owner key. When keys change, define exactly which ownership changes.
- **Observability:** an observable snapshot read invalidates Composition when necessary; plain `Memory` intentionally does not. Don't claim reactive semantics for all `Storage<S>`.
- **Composite writes:** model multi-memory backing via explicit assemble/put; test Get–Put, Put–Get and Put–Put only if claiming a lawful Lens, otherwise use `Selection`. Reproduce a two-field update that the legacy exclusive `when` would skip.
- **Action projection:** `(store * l1) * l2` agrees observationally with `store * (l1 * l2)` where Lens laws hold, including envelope metadata.
- **Coroutine ownership:** the outer Application Flow's structured interpreter maintains at most one owned processor, cancels it on owner disposal, and doesn't duplicate subscriptions on recomposition.
- **Effects:** dispatch acknowledgement vs completion, failures, retries and process lifetime must be documented. Cancellation remains cancellation.
- **UI lifecycle:** test subtree mount/unmount, login/logout, changed owner keys and nested Lens-based page views.
- **Isolation:** common MathCat code remains Compose-free; new compiler/runtime dependencies are pinned in `gradle/libs.versions.toml` only after compatibility with the current Kotlin version has been verified.

## Delivery sequence

**S1:** characterize Solyton state and collector behavior with tests.  
**S2:** implement Compose-runtime snapshot Storage + receiver DSL as optional module.  
**S3:** bind the external Application Flow and test structured lifecycle.  
**S4:** pilot one Solyton feature behind a reversible switch and publish only after JS/Compose lifecycle and consumer smoke tests pass.

Use the shared [Evolution Flow proposal](EVOLUTION-FLOW.md), **but do not invent a Store-internal machine**. An ordinary thin Composable Store wrapper may be all that is needed.

## Persistent browser data is an external boundary

The Compose Store must not silently read from or write to cookies,
`localStorage`, `sessionStorage` or IndexedDB while rendering. The
surrounding Application Flow performs hydration and persists explicit
commands; the Store merely exposes the resulting state to the UI.
See [Browser Persistence](BROWSER-PERSISTENCE.md) and
[IndexedDB/Offline](INDEXEDDB-OFFLINE.md).
