# ToolKit Actions — typed, composable descriptions (draft)

This Kotlin Multiplatform module provides a minimal transport-neutral Action
abstraction and integrates it with the already-tested generic Store capability.

## Public API sketch

```kotlin
import evoleq.toolkit.actions.*
import evoleq.toolkit.store.Store

val root: Store<Application, ActionDispatcher<Application>> =
    Store(storage = applicationStorage, dispatcher = applicationDispatcher)

val banking = root * bankingLens
banking.read()
banking.write(newBankingState)
banking.dispatch(readBankAccountsAction)
```

The `Store * Lens` overload is intentionally located in **toolkit-actions**.
The generic `toolkit-store` module still works with arbitrary dispatcher
capabilities and is not forced to know the semantics of typed Actions.

## Responsibilities and algebra

- `Action<S,I,O>` contains a `name`, `Reader<S,I>`, `Writer<S,O>`,
  and optional `onSuccess: Writer<S,O>`. It **describes** how an external
  effect interacts with state; it **does not execute** the effect.
- `Lens<S,A> * Action<A,I,O>` lifts both reader, writer and success handler
  to the whole state using the existing optics algebra.
- `ActionEnvelope<S,I,O>` contains id, parentId, ordered follow-ups,
  deferred follow-up factories, metadata and run/cleanup flags.
  `Lens * ActionEnvelope` lifts **all** children without executing
  deferred factories. Subsequent invocation evaluates the original factory
  then lifts its result.
- `ActionDispatcher<S>` has two suspend functions: `dispatch(action)` and
  `dispatchEnvelope(envelope)`. A supplied sink (e.g. a recording buffer)
  decides delivery semantics.
- `ActionDispatcher<S> * Lens<S,A>` delegates **lifted** local messages
  to the original dispatcher; it does not allocate a new processing queue.
- `Store<S,ActionDispatcher<S>> * Lens<S,A>` combines both lifts and retains
  access to `read()/write()` via `Storage` delegation.

For lawful Lens `l` and an Action `a`, both `l * a` and the nested
application `(l1*l2)*a` are defined by Reader/Writer composition. On pure
state interactions, successive and one-step lifting should be equivalent.
For effects, equivalence depends on the runtime interpreter and envelope
semantics; the tests verify concrete fields, not universal equivalence.

## Deliberate non-goals

**Not a drop-in replacement for legacy Solyton Action.** The legacy Action
contains Ktor endpoint classes, serializers, HTTP-specific errors and
callbacks; this module presently contains none of those transport contracts.
In particular, `onSuccess` is only a described Writer: a later processor
must define when it is run. There is no Action execution or completion promise
in a dispatcher that merely accepts an envelope.

There is **no** HTTP backend, service resolver, `MutableSharedFlow`,
CoroutineScope, Compose lifecycle, process registry, retry/transaction
policy, stream observer, or automatic migration to Solyton.

**Maturity policy:** Evoleq/ToolKit is developed and tested first, the book
is written in parallel, and Solyton only adopts reviewed, mature components.
Nothing is merged into `master`, published or integrated by this draft.

See [Action and Storage architectural research](../docs/ACTION-DISPATCH-AND-STORAGE.md).

## Profunctor shape of Action

For fixed state `S`, ToolKit's
`Action<S, I, O> = Reader<S, I> × Writer<S, O>` is **covariant in I** and
**contravariant in O**. It is a profunctor in standard argument order
`P_S<O, I> = Action<S, I, O>`, not `P_S<I, O>`.

Pure `map(I -> J)`, `contraMap(P -> O)`, and
`dimap(before: P -> O, after: I -> J)` preserve the Action's name and
also transform `onSuccess` on the writer side. Tests cover identity and
composition laws, including both writers.

These operations transform a **pure Action descriptor**, not an HTTP codec.
When binding a transformed Action to a transport endpoint, the declared
request/response types must still match the transformed input/output.
