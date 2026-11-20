# Action dispatchers and compositional Storage

**ToolKit 2.0 — architecture exploration, 2026-11-18.**  
**Status: research and incremental implementation.** The original text below distinguishes design candidates from verified code. The separate [Store draft PR #4](https://github.com/evoleq/mathcat/pull/4) has implemented `Store<S,D>` in `toolkit-store`; the [typed Actions draft PR #5](https://github.com/evoleq/mathcat/pull/5) now adds the small `toolkit-actions` module, with standalone `Action`, `ActionEnvelope`, `ActionDispatcher`, their typed Lens transports and `Store * Lens`. Neither PR adds a running processor, coroutine scope, HTTP integration, Compose adapter or a Solyton migration. Both remain drafts.

## The problem

The existing Solyton frontend has a public `Storage<Application>` whose `read()` assembles several independent (some Compose-observable, some plain) Memories and whose `write()` routes updates. An application-level `MutableSharedFlowActionDispatcher<Application>` currently lives **inside the Application data class**. A `@Composable Storage()` factory registers a collector via `onDispatch`, which launches `ProcessAction(envelope) runOn storage`. Sub-application representations (e.g., `NavBar`, `BankingApplication`) each receive a specially lifted dispatcher when projected from `Application`.

What we want: preserve this practical composability, but separate **data**, **the capability to dispatch commands**, and **the lifecycle/runner that executes them**. Do not make ToolKit core depend on Ktor, Compose, coroutines or serializers.

## 1. Three separate capabilities

```text
Storage<S>              read : () -> S; write : S -> Unit
ActionDispatcher<S>     dispatch : ∀ I,O. Action<S,I,O> -> Effect<Unit>
ActionProcessor<S>      consumes action envelopes, executes effects, updates Storage<S>
```

Here `Effect` is deliberately an informal placeholder: real implementations could use `suspend` (Kotlin), an event queue, or a testing interpreter. Do **not** introduce an unimplemented `Effect<F>` type into the pure core.

A potential **combined capability** is `Store<S>` (name **not finalized**):

```kotlin
// API illustration only — not implemented
class Store<S : Any>(
    val storage: Storage<S>,
    val actions: ActionDispatcher<S>
) : Storage<S> by storage {
    suspend fun <I : Any, O : Any> dispatch(action: Action<S, I, O>) =
        actions.dispatch(action)
}
```

This gives the concise API:

```kotlin
store.read()
store.write(newState)
store.dispatch(someAction)
```

Unlike embedding a dispatcher in a model `data class Application`, `Store<S>` exposes a capability externally. `Application` can remain just data; the dispatcher need not be copied into `Application`, serialized, compared for equality or reset when transforming a domain view.

`Store<S>` is an **effectful product of capabilities**, not `Storage<S>` for one mutable cell and not a categorical product in a category with unspecified effects. Its name may collide with the legacy `@Composable Store` function in other packages; import aliases or a more specific name can be chosen after usage tests.

The pure `toolkit-core` retains only `Storage<S>`, `Reader`, `Writer`, `Lens`, `Selection`, `State`, etc. The action model and combined capability would belong to a separate module such as **`toolkit-actions`**. External executors, API calls, Compose scopes, persistence, queue backpressure and process registry belong in application/runtime adapters.

## 2. The same Lens can transport both capabilities

Current Evoleq already provides:

```text
Storage<S> * Lens<S,A>           -> Storage<A>
ActionDispatcher<S> * Lens<S,A>  -> ActionDispatcher<A>
Lens<S,A> * Action<A,I,O>        -> Action<S,I,O>
```

The last operation **lifts a substate action to the whole state**.

Write `l : Lens<S,A>`. For a substate action `a : Action<A,I,O>`, its root action `lift_l(a)` has:

```text
rootReader(s)      = a.reader(l.get(s))
rootWriter(out)(s) = l.set(a.writer(out)(l.get(s)))(s)
```

The root dispatcher `δ_S` induces a focused dispatcher `δ_A`:

```text
δ_A(a) = δ_S(lift_l(a)).
```

This is **reindexing an action by the Lens**. The dispatcher does not need to know about the storage representation; the processor ultimately uses the bound root Storage to execute the lifted action.

Thus an intended fluent composition is:

```kotlin
// API illustration only — not implemented
operator fun <S : Any, A : Any> Store<S>.times(
    lens: Lens<S, A>
): Store<A> = Store(storage * lens, actions * lens)

// Consumer
val banking = applicationStore * bankingLens
banking.read()
banking.write(newBankingState)
banking.dispatch(readBankAccounts())
```

**This is the central DX goal:** no need to extract `application.actions` from domain data, call `actions * bankingLens` manually or carry the parent Storage through every Action call.

### Laws and assumptions

If Lens composition is lawful and action lifting preserves all Action data and semantics, focusing successively should agree with focusing once:

```text
(store * l1) * l2  ≈  store * (l1 * l2).
```

For the storage read/write part the relation follows from the Lens laws. For dispatch it requires coherent action lifting and preservation of the **entire envelope** (metadata, child actions, flags, callbacks). `≈` here refers to equivalent observable dispatch behavior under an agreed effect semantics, **not** object identity or synchronization guarantees. We should test this using a recording dispatcher with no network calls.

A `Selection` without laws may still produce a readable/writable focused Storage, but the analogous **action lifting must be considered separately**: an Action interpreter might assume lawful whole-state updates. Do not automatically bless `ActionDispatcher<S> * Selection<S,A>` without clarifying those assumptions.

## 3. Product of Storages ≠ product of Dispatchers

Two independent storages can give a value product:

```text
Storage<A> × Storage<B>  → Storage<A × B>    (candidate x)
```

But two ActionDispatchers cannot automatically process an **arbitrary** joint `Action<A×B,I,O>`: the Action may update both components, and there is no universal way to split its endpoint, effects and output Writer into two independent commands.

What *is* natural is routing a **tagged sum of commands**:

```text
Command<A> + Command<B>
    → Left  -> Dispatcher<A>
    → Right -> Dispatcher<B>
```

This is exactly where the algebra of **sums of commands** complements **products of states**. A future product Store could therefore have:
- `Storage<A×B>` as its read/write value capability; and
- a dispatcher for tagged `A`-commands or `B`-commands, **or** a separate orchestrator explicitly capable of handling a combined Action.

That is a **design choice**, not an automatic law that product of two `Store` values gives `Store<A×B>` for every possible Action.

The effective dispatch destination and ownership of queues must be explicit, especially when one existing dispatcher is shared by many nested Storages, or when two sources have separate runtimes. A product must not silently start two new event collectors.

## 4. State transitions vs. effects vs. process management

Existing `ProcessAction` demonstrates an attractive Kleisli chain:

```text
Read -> RegisterProcess -> Call -> OnError -> Dispatch -> React -> ReactEnvelope
```

The mathematical idea is worth preserving:

```text
KlState<Storage<Application>, A, B>
```

is a state-threading representation of operations that can update the storage handle and pass results along. It should not force all actions to be HTTP actions. In the current Evoleq code `Action<Base,I,O>` includes Ktor `HttpStatusCode`, endpoint `KClass`, Kotlin serialization and an error writer; these are **transport/API-adapter decisions**. A future core action definition, if needed, should separate:
- request projection from state (Reader),
- response/application update (Writer),
- effect specification/interpreter,
- envelope/correlation and process lifecycle.

Even if `dispatch` is `suspend`, merely enqueueing an action is not the same as executing it. We should choose explicitly whether the return value means **accepted**, **completed**, or **failed**, and who owns cancellation and retries.

Do not start a `CoroutineScope(Job()).launch` implicitly inside a composed Storage, Lens, or `Store` constructor. A lifecycle owner (Compose `LaunchedEffect`, application scope, actor engine, or explicit runner) should start and stop the processor once.

## 5. Findings in existing Evoleq / Solyton (read-only, not ported)

1. **Already compositional:** `evoleq/.../optics/storage/flow.kt` implements `ActionDispatcher<Whole> * Lens<Whole,Part>`, using `Lens * Action`; `action.kt` implements the corresponding Reader/Writer lift. Preserve this pattern.
2. **Envelopes lose fields when lifted:** `ActionEnvelope` includes `next`, `nextLazy`, `run`, `clearOnFinish`, plus `id`, `parentId`, and `meta`. The current `ActionDispatcher.times(lens).dispatchEnvelope` reconstructs an envelope copying **only** `action, meta, id, parentId`. It appears to discard child actions, deferred children and execution/cleanup flags. Action-envelope lifting should preserve semantics **recursively**, not only copy selected metadata. This is an identified source-code issue; we have not run a reproduction.
3. **Action lift drops an optional callback:** `Lens.times(Action)` constructs the new Action including `onError` and `failOnError`, but does not pass `onSuccess` (which exists on the Action data class), so the optional callback may be dropped. A faithful lift should preserve and appropriately transport every state-dependent field.
4. **Domain data mixes in dispatcher capability:** `Application.actions`, and its submodels `NavBar.actions` and `BankingApplication.actions`, carry a dispatcher. In `navBarIso` and `bankingApplicationIso`, `get` constructs a new lifted dispatcher and `set` does not persist changes to that dispatcher. Treating these transforms as **fully lawful Lens** (rather than a view with injected context) deserves scrutiny. Separating the action capability into a Store can eliminate this artificial field from the data projection without changing the current frontend immediately.
5. **Event and collector lifecycle:** `solawi-bid/.../application/storage/event/dispatch.kt` starts a detached `CoroutineScope(Job())` when the Storage factory invokes `onDispatch`. Recomposition can potentially re-register collectors. Refactoring requires tests, explicit owners and cancellation; no current runtime duplicate-collector issue is asserted as proven.
6. **Kleisli execution is application-specific:** `solawi-bid/.../middleware/action-processor.kt` sequences `Read`, `Call`, `Dispatch`, `React` etc. A future interpreter should retain this decomposition while living outside the pure Storage core.

## 6. Candidate tests before any API implementation

- `Store<S>` delegates `read/write` unchanged to its constituent Storage and does not allocate a new Memory.
- `Store<S> * Lens<S,A>` focuses both `read/write` and dispatch; the dispatcher actually receives a root-typed lifted action.
- Sequential focus versus composed focus produces observationally equivalent dispatched **action and envelope** on a recording interpreter.
- `id`, `parentId`, `meta`, `next`, `nextLazy`, `run`, `clearOnFinish` and callbacks survive action lifting with defined semantics.
- A product of independent storages can route a tagged sum of commands to the correct dispatcher. An arbitrary whole-product Action is not silently assumed splittable.
- `@Composable` app scope starts exactly one collector and cancels it when no longer needed. Recomposition must not duplicate processing.
- An action which changes multiple independent Memories remains compatible with the write-router laws or explicitly reports partial effects.
- Suspend dispatch has a clear contract for backpressure, cancellation, errors and delivery guarantee; “dispatched” does not automatically mean “completed.”

## API decisions deliberately postponed

- `Store<S>` versus `ActionStore<S>` (avoid collision with Compose `Store` constructor).
- Whether an abstract `Action` protocol is necessary, or a smaller tagged command interface is better. Keep transport-specific Action and interpreters separate.
- Interface/capability combination (`Store<S> : Storage<S>` delegation) versus a plain record; ergonomics favored but to be proven at client call sites.
- Whether Actions for arbitrary whole product states are supported by a first-class orchestrator.
- Whether any `Dispatcher` should be typed only by a *command* rather than by stored *state*; this may simplify sum routing in future.
- Ownership of queue subscription and processing, and interoperability with Android/iOS/native coroutine scopes.

**No Solyton frontend changes, PR merges, runtime adapter or new published API are made by this research document.**

## References

- [ToolKit Storage composition design](STORAGE-COMPOSITION.md)
- [ToolKit Storage and Memory review](STORAGE-AND-MEMORY.md)
- [ToolKit mathematical foundations](MATHEMATICAL-FOUNDATIONS.md)
- Existing dispatcher & envelope: https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/optics/storage/flow.kt
- Existing Action lens lift: https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/optics/storage/action.kt
- Existing frontend action processor: https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/middleware/action-processor.kt
- Existing frontend Storage: https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/storage.kt

## 7. First implementation slice (PR #5)

The experimental `toolkit-actions` module now implements a **transport-neutral subset** of the Evoleq architecture:

```text
Action<S,I,O>                     name, Reader<S,I>, Writer<S,O>, onSuccess?
ActionEnvelope<S,I,O>             action, id, parentId, next, nextLazy,
                                  meta, run, clearOnFinish
ActionDispatcher<S>                suspend dispatch / dispatchEnvelope

Lens<S,A> * Action<A,I,O>         -> Action<S,I,O>
Lens<S,A> * ActionEnvelope<A,I,O> -> ActionEnvelope<S,I,O>
ActionDispatcher<S> * Lens<S,A>   -> ActionDispatcher<A>
Store<S,ActionDispatcher<S>> * Lens<S,A>
                                  -> Store<A,ActionDispatcher<A>>
```

`Action` descriptions **do not include a runnable endpoint or a response
interpreter**. This is deliberate: the legacy Evoleq API has Ktor-specific
descriptors, serializers and errors. The new mathematical representation does
not claim to execute those effects or replace that type one-for-one.

The envelope lift preserves the `id`, `parentId`, metadata, run/cleanup
flags, recursively lifts all eager children, and wraps deferred suppliers
rather than evaluating them. The Action lift transports the `onSuccess`
Writer along with the main Reader and Writer. Common tests check these
behaviors and demonstrate a recording dispatcher, which only accepts messages.

**Future work, intentionally not implemented yet:** typed endpoint/effect
description, response/result interpretation, explicit error policies,
cancellation ownership, persist/retry semantics and compatibility with
existing Evoleq Action/ActionEnvelope types. No backend or Solyton application
code was edited. Testing and API review are required before merging PR #5.
