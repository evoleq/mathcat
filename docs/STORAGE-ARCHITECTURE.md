# MathCat — Storage and Source

**Date:** 2026-11-18 · **Status:** Draft · **Stacked on:** [MathCat PR #2](https://github.com/evoleq/mathcat/pull/2)

## Why a separate module?

`State<S,A>` is a **pure description** of a state computation: `S → (A,S)`, available in `mathcat-core`. `Storage<S>` holds **effectful, synchronous read/write callbacks** to mutable external state. That boundary belongs in the separate Kotlin Multiplatform module **`mathcat-storage`**. The implementation deliberately has no platform API, Android-only dependency or Coroutine dependency.

```text
org.evoleq.mathcat.core.reader.Source<T>     = Reader<Unit,T>
org.evoleq.mathcat.core.writer.Dispatcher<A> = Writer<Unit,A>
org.evoleq.mathcat.storage.Storage<S>        = (read: () -> S, write: (S) -> Unit)
```

**Mathematical perspective:** `Reader<Unit,T>` is `Hom(Unit,T)`. In the category of sets this is naturally isomorphic to `T`, but in Kotlin, evaluation may have observable effects. A `Source` therefore has practical deferred-computation semantics that an eagerly materialized value `T` does not.

**Kotlin type-system precision:** `typealias Source<T> = Reader<Unit,T>` is **not a new nominal type**; the compiler can substitute the underlying Reader type. The abstraction is valuable because of its explicit algebraic meaning, named constructor and `emit` API. If independent overload discrimination for Source is ever required, a distinct `fun interface` could be considered later, but this slice deliberately preserves the original Evoleq alias.

`Source<T>` was already present in the legacy Evoleq library, but missing from early MathCat. The factory `Source { ... }` and `emit()` have now been restored. **A Source re-evaluates when emitted**, so it is not a stored snapshot and not the same as the value `T`.

`Dispatcher` in this context is a command-indexed Writer over Unit, **not** a coroutine dispatcher. Its `dispatch(command)` executes an effect.

## Composition

```text
Storage<S> * Lens<S,A>       → Storage<A>
Storage<S> * Selection<S,A>  → Storage<A>
Storage<S> * Reader<S,A>     → Source<A>
Storage<S> * Writer<S,A>     → Dispatcher<A>
Read(Storage<S>)            → Source<S>
Dispatch(Storage<S>)        → Dispatcher<S>
```

This preserves the production-proven Evoleq usage:

```kotlin
val focus = storage * partLens * nameLens
focus.write("Ada")

val read: Source<String> = storage * Reader<Whole, String> { it.part.name }
val current = read.emit()

val dispatch = storage * partLens * Writer<Part, String> { name ->
    { part -> part.copy(name = name) }
}
dispatch.dispatch("Babbage")
```

The notation `Storage * Lens * Lens` builds nested **live views**: reads access the latest outer state; writes read the outer state once at their time of execution and reconstruct it through `Lens.set`. A Selection works similarly but does not promise Lens laws. This is a useful operational composition; it does **not** turn an arbitrary Selection into a Lens.

## What is *not* guaranteed

- **No atomicity:** `write(lens.set(value)(read()))` is a read–modify–write sequence. Concurrent writers can lose changes.
- **No exception/cancellation semantics:** callbacks can fail; no transaction or rollback, and no `suspend`/Coroutine semantics.
- **No implicit equality/laws:** combining two mutable storages has no guaranteed Lens laws or side-effect-free behavior.
- **No snapshots:** Sources and focused Storages are dynamic; consumers must explicitly read a snapshot if needed.
- **No reactive observation:** `Source` is an evaluation abstraction, not Flow, StateFlow or Compose State.
- **No platform UI/storage engines:** Android DataStore, iOS persistence, browser storage and database adapters belong in separate modules later.

## Multiplatform status

The module declares JVM, JS, WasmJS, Android (AGP 9 KMP Library plugin), Linux, Windows, iOS and macOS targets, mirroring `mathcat-core`. CI includes JVM, JS, Wasm, Linux, Windows native tests, Android compilation and Apple compilation/macOS tests. **Check the PR's live job results before claiming support on a platform.**

No publishing, license or migration decisions are made in this PR.

## Evidence and follow-up

- Old Evoleq: `org.evoleq.math.Source`, `org.evoleq.optics.storage.Storage`, and `org.evoleq.optics.transform.storage-{lens,reader,writer}`.
- New tests: `SourceTest`, `DispatcherTest`, `StorageTest` across commonTest.
- Next: concurrency-/atomic-storage alternatives, async/suspend variants and reactive adapters **only after** real usage analysis; do not introduce `suspend` into pure algebra by default.

**Log (2026-11-18):** Restored Source and Dispatcher as aliases, added live Storage composition in a separate KMP module, and configured CI. Initial JVM/Windows test run failed in `storageReaderCreatesLazySourceThatSeesFreshValues`: the assertion expected lowercase `second` after `uppercase()`; the correct expectation is `SECOND`. Corrected in the stacked draft. The project also corrected the Android KMP compile task name from `compileKotlinAndroid` to `compileAndroidMain`. Final all-platform CI status is established by the latest [PR checks](https://github.com/evoleq/mathcat/pull/3/checks), not by this interim log.
