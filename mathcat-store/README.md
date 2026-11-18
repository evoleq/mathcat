# MathCat Store — experimental capability pairing

A Store pairs an existing Storage<S> with a replaceable dispatcher D.
It does not allocate new Memory, require HTTP or Compose, or start a runner.

```kotlin
val store = Store(storage = existingStorage, dispatcher = existingDispatcher)
val focused = store.focus(partLens) { dispatcher, lens ->
    dispatcher * lens // use existing Evoleq dispatcher lift
}
val recording = store.withDispatcher(recordingDispatcher)
```

The transport callback is explicit while typed Action/ActionEnvelope contracts
are under review. An action-specific adapter can later provide Store * Lens.

Replacing a dispatcher makes a new Store; existing focused Stores keep their
previous dispatcher. For late-bound switching, a Source<D> can resolve the
current dispatcher at dispatch time.

**Draft implementation; no Solyton integration.** See the architecture note
at ../docs/ACTION-DISPATCH-AND-STORAGE.md.
