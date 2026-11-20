# Evolution Flow — an automaton around the Store

**2026-11-20 · Design plan; NOT implemented.**

**Decision:** preserve the mathematical heart of the original Evoleq Flow, modernize its execution model, and keep lifecycle ownership **outside** `Store<S,D>`. The Application Flow owns a routing subautomaton and uses the Store as an existing capability.

## Existing Evoleq idea

The author's [`evoleq/evoleq-core Flow<D,T>`](https://github.com/evoleq/evoleq-core/blob/master/src/commonMain/kotlin/org/drx/evoleq/evolution/flow.kt) combines `EvolutionConditions<D,T>` and a Kleisli endomorphism `KlEvolving<D,D>`. The [evolution function](https://github.com/evoleq/evoleq-core/blob/master/src/commonMain/kotlin/org/drx/evoleq/evoleq.kt) iterates `D[n+1] = Φ(D[n])` while a condition holds. [`SimpleProcessFlow`](https://github.com/evoleq/evoleq-core/blob/master/src/commonMain/kotlin/org/drx/evoleq/evolution/flows/process/simple-process-flow.kt) and [the earlier Evoleq-FX application flow](https://github.com/doctor-smith/evoleq-fx/blob/master/src/main/kotlin/org/drx/evoleq/fx/flow/fx-application-flow.kt) demonstrate explicit process and lifecycle phases.

This is **not** `kotlinx.coroutines.flow.Flow`. Preserve explicit evolution/conditions/phase transitions; do **not** port the historical coroutine implementation unchanged. In older repositories, independently created `CoroutineScope(Job())`, some `GlobalScope` usage, sleeping/polling and detached work obscure resource ownership.

## Distinguish two mathematical patterns

Autonomous evolution:

```text
Φ : S -> Effect<S>
S[n+1] = Φ(S[n])                  while Continue(S[n+1])
```

Event-driven routing and lifecycle: a Mealy-style transition,

```text
δ : Phase × Event -> Phase × List<Command>
```

where commands are *descriptions* of effects, not executed inside `δ`. Implement actual effects in a separate interpreter. A completed asynchronous command returns as a **new event**; asynchronous callbacks never mutate the state behind the reducer's back.

### Ownership hierarchy

```text
          ApplicationFlow (owns lifetime and event loop)
          +------------------------------------------------------+
Events -->|  Application automaton / dispatcher of events        |
          |      +----------------------------------------+      |
          |      | Routing subautomaton                  |      |
          |      | match -> guard -> load -> activate    |      |
          |      +----------------------------------------+      |
          |                                                      |
          |  uses Store<S,D> : Storage<S> × D                     |
          |  uses browser, plugin, auth effect interpreters       |
          +------------------------------------------------------+
                                  |
                           observed projection
                                  v
                             Compose UI
```

The **Store is a capability used by the Flow**, not the owner of an automaton. Routing belongs **inside the Application Flow**, not necessarily *inside* the Store's domain data. A host may project current navigation state through `Storage * Lens` or an observable adapter; route definitions and browser-history handles remain external to domain state.

A router may be a subautomaton with its own **pure phase transition function** while sharing the outer Flow's event loop and lifecycle. No independent scope is required merely to represent this subautomaton.

## Closure and interpreter

A small host-owned closure may retain current automaton state, one serialized event inlet, operation generation IDs and effect handles. The transition function remains externally testable:

```kotlin
// Illustrative architecture — NOT existing MathCat API.
val runtime = ApplicationFlow(
    initial = AppPhase.Initial,
    store = existingStore,
    transition = ::transition,
    interpret = applicationEffects,
    scope = ownerScope
)
runtime.send(AppEvent.Navigate("/app/shares/42"))
runtime.stop()
```

The closure is **stateful**, not pure; only `transition` is pure. Avoid naming the closure's imperative send method a mathematical endomorphism. Expose the intended operations without a large generic framework.

## Concurrency and cancellation invariants

- The **host supplies** a parent CoroutineScope; any child Job is a child of it. No `GlobalScope`, implicit detached `CoroutineScope(Job())`, busy waiting or blocking `Thread.sleep`.
- Handle **one state transition at a time**. Choose and document sequentiality, queue bounds, backpressure and cancellation policy (e.g. a bounded Channel with one consumer). Do not launch an uncontrolled coroutine for every event.
- Long-running plugin loading and authorization may be cancellable child effects. Return completion events tagged with a **generation/request ID**, discarding stale results when the route changes.
- Stop is idempotent: prevent further event acceptance, cancel owned work and dispose owned plugin sessions once. A borrowed Store is **never** disposed by inference.
- Cancellation is propagated, not converted into a normal application error. Expected domain failure becomes a typed event and/or explicit sum result.
- State observation is an adapter responsibility; Compose snapshot, StateFlow and testing callbacks may be distinct interpreters.
- `Trampoline` is only needed for genuinely deep synchronous recursive composition, not for an ordinary structured coroutine event loop.

## Work plan

**F1 — two pure reducers first.** Write real routing and application-runtime transition examples and tests. Define legal transitions, errors, terminal states, command descriptions, and laws such as deterministic replay under pure inputs. Resist generalizing too early.

**F2 — closure-owned event loop.** Implement the smallest runner that can execute the same reducer type, with host-owned scope and explicit stop. Tests: serialization, cancellation, backpressure, failure, no second load for same active plugin, stale completion protection, and no post-stop commands.

**F3 — integrate routing as subautomaton.** Route navigation events through the Application Flow; let the router return commands for browser history, permission checks and optional MathCat `AccessPlugin`. Do not silently promote the route registry into Application domain data.

**F4 — integrate Compose Store.** Bind Compose observation and a single application-owned processor to the Flow, not to the `Store` constructor. Demonstrate recomposition, teardown, owner-key changes and cancellation.

**Extraction gate:** Name and publish a small `mathcat-evolution` module only after both examples demonstrate that sharing the reducer/runner saves complexity. Otherwise keep the reducers local to adapters.

## Executable specification

Tests must prove transition determinism, state-phase validity, terminal-state behavior, ordered effect descriptions, one load per active plugin, no lost events, controlled queue overflow policy, cancellation, idempotent disposal, stale completion rejection and **no leaked coroutine** after Composition disposal.

## Persistence effects owned by this Flow

Web Storage, cookies and IndexedDB are effect interpreters, not State/Lens
implementations. Startup hydration, preference persistence, context updates,
database migration and eventual offline synchronization belong to the
Application Flow's event/command lifecycle. For the concrete split and safety
constraints see [Browser Persistence](BROWSER-PERSISTENCE.md) and
[IndexedDB/Offline](INDEXEDDB-OFFLINE.md).
