# Running a pure Flow under an application-owned lifecycle

**Experimental Kotlin Multiplatform module:** `toolkit-evolution-runtime`,
built on the `toolkit-evolution` algebra. A Flow is a **pure reducer**;
a runtime interprets its returned commands. The Store remains a borrowed
capability, not a coroutine owner.

## Public usage

```kotlin
sealed interface Event {
    data object Start : Event
    data class EnvironmentReady(val name: String) : Event
}
sealed interface Command {
    data object ReadEnvironment : Command
}
data class Phase(val environment: String? = null)

val app = Flow<Phase, Event, Command> {
    on<Event.Start> { phase, _ ->
        Evolve(phase, Command.ReadEnvironment)
    }
    on<Event.EnvironmentReady> { phase, event ->
        Evolve(phase.copy(environment = event.name))
    }
}

val runtime = Run(
    flow = app,
    initialState = Phase(),
    scope = applicationScope, // owned by the host and contains a Job
    queueCapacity = 64
) { command, report ->
    when (command) {
        Command.ReadEnvironment -> {
            val value = loadEnvironment()
            report(Event.EnvironmentReady(value))
        }
    }
}

runtime.send(Event.Start)
// UI can observe runtime.state as StateFlow<Phase>.
// On disposal: runtime.stop(), and host cancels applicationScope separately.
```

No `runBlocking`, `GlobalScope`, detached `CoroutineScope(Job())`, polling,
HTTP response handling or platform-specific globals are hidden in this DSL.

## Runtime contract

```text
     host CoroutineScope
           │
           ▼
      child SupervisorJob
           │
           ├── one Channel<E> ──► serialized transition consumer
           │                            │
           │                            ▼
           │                      StateFlow<S>
           │                            │
           │                            ▼
           └──── structured commands ◄── C*
                   │
                   ▼
          effect interpreter
                   │
                   └──── report(E) ───► same Channel<E>
```

A single consumer updates state. Commands from **one** transition are
interpreted in their declared list order. Commands from **different**
transitions may overlap; this is essential for a long-running plugin load
not to block a new Navigate event. Their ordering is **not** globally
serial or transactional, and the host must use typed generation IDs or
its own cancellation policy to reject stale effect results. The pure
Router in PR #27 demonstrates the generation mechanism.

The runtime creates a `SupervisorJob(parentJob)` and never cancels
the parent. `stop()` closes event admission and cancels its own child
jobs; it is idempotent. Cancellation is propagated, **not** converted
to an ordinary failure event. This small runner does not own or dispose
a borrowed Store or an already loaded/shared plugin.

## Explicit capacity and failure policies

- `send(event)` suspends when the event queue is full and throws when
  the runtime is closed. It does not silently drop events.
- `offer(event)` never suspends and returns `false` when full or closed;
  **the caller** must choose whether to retry, reject, or show overload.
- Queue capacity is explicit: a default of 64, 0 for rendezvous, and
  a guarded maximum of 65536. There is no unlimited hidden buffer.
- For an expected command failure, the application may pass
  `onCommandFailure = { command, error -> AppEvent.EffectFailed(...) }`.
  This yields a typed event through the same channel.
- Without an error mapper, unexpected interpreter exceptions are
  rethrown in their structured effect child rather than falsely
  reporting success. A SupervisorJob preserves other work; the host
  must provide its monitoring/error policy.
- A thrown exception from the pure reducer terminates its event
  consumer and closes the inbox. This is an implementation fault,
  not a normal Domain failure.

## Executable documentation: FlowRuntimeTest

| Test | Contract |
| --- | --- |
| `typedCompletionEventsComeBackThroughTheSameSerializedReducer` | One channel handles interpreted effect results |
| `eventTransitionsAreSerializedWithoutLostStateUpdates` | One state writer; no lost increments |
| `commandsAreInterpretedInDeclarationOrderWithinOneTransition` | Intra-transition command order |
| `slowEffectDoesNotBlockFurtherStateTransitions` | Async effects don't block the event loop |
| `boundedOfferReturnsFalseInsteadOfSilentlyDroppingOrGrowing` | Bounded backpressure |
| `zeroCapacityMeansRendezvousNotUnboundedBuffering` | Explicit rendezvous queue |
| `expectedEffectFailureCanBeTranslatedIntoATypedEvent` | Failure mapping is caller-controlled |
| `cancellationDoesNotGetTranslatedIntoAnOrdinaryFailureEvent` | Cancellation semantics |
| `stopIsIdempotentAndRejectsFutureEventsWithoutCancellingTheHost` | Stop and borrowed parent ownership |
| `cancellingTheHostAlsoStopsTheChildRunner` | Structured child lifetime |
| `invalidCapacityIsAnExplicitConfigurationError` | Validated initialization |
| `snapshotsAreReadOnlyObservationsNotStoreOwnership` | Snapshot observation is not State/Store mutation |

## Limits and integration gate

This is **not** an automatic application bootstrap, browser router,
plugin registry, global dispatcher, task scheduler or process manager.
No lifecycle method is invoked from a Composable body.

Before using the runtime in Solyton, an end-to-end test must cover:
bootstrap Environment/Version/I18n/Login, guarded direct deep links,
navigation while loading, browser back/forward and reload,
plugin activation once, listener cleanup, owner cancellation,
login/logout, repeated Compose recomposition and simultaneous
observable/plain Storage updates. Those are the actual migration
conditions, not assumptions drawn from algebraic associativity.

Browser persistence and IndexedDB remain **separate effect
interpreters** with dedicated failure and transaction semantics.
See `docs/plans/BROWSER-PERSISTENCE.md` and
`docs/plans/INDEXEDDB-OFFLINE.md`.
