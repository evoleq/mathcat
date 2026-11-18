# Storage, Memory and Compose boundaries

**MathCat 2.0 · 2026-11-18 · Draft PR #2.** An implementation and architectural note, not an automatic migration of existing Solyton code.

## A simple algebraic boundary

```text
Source<T>  = Reader<Unit,T>                 (a deferred read)
Storage<T> = { read: () -> T, write: T -> Unit } (a read/write capability)
Memory<T>  implements Storage<T>            (one plain mutable backing cell)
```

The braces express **a record of capabilities**, not an algebraic coproduct. A Storage need not own its backing value directly: it may assemble a view from several independent Memories.

The actual API is:

```kotlin
interface Storage<S> {
    fun read(): S
    fun write(value: S)
}

class Memory<S>(initial: S) : Storage<S>

fun <S> Storage<S>.source(): Source<S> = Source { read() }
```

`Source<T>` is **already present in legacy Evoleq**, and is now restored to MathCat as exactly `typealias Source<T> = Reader<Unit,T>`. It is a typealias rather than a wrapper, so `Source * Reader` is ordinary Reader composition. **`read()` and `write()` are the required Storage operations.** The optional `storage.source()` projection constructs a live, deferred Source, not a property or an extra obligation on backend implementers. Previously obtained Sources observe later reads of the same Storage, not frozen snapshots.

The named `Storage(source, write)` and `Storage(read, write)` factory functions make adapters easy without subclassing.

### Composition

```text
Storage<S> * Reader<S,A>        → Source<A>
Storage<S> * Lens<S,A>          → Storage<A>
Storage<S> * Selection<S,A>     → Storage<A>   (no Lens-law guarantee)
```

For a lawful Lens `l : S → A` and `storage : Storage<S>`:

```text
(storage * l).read()   = l.get(storage.read())
(storage * l).write(a) = storage.write(l.set(a)(storage.read()))
```

For lazy observation, `(storage * l).source().emit()` evaluates to the same projected value as `(storage * l).read()`. The projection is defined in terms of `read()`, not vice versa.

**Important:** A focused write executes a read–modify–write sequence which is **not atomic**. Concurrent writers require a stronger transactional/atomic storage abstraction later. Plain `Memory` is not thread-safe.

## A single Storage over multiple Memories

The current Solyton frontend is not merely a `Memory<Application>`. It manages several **independent backing stores** and exposes one `Storage<Application>` as a synthesized view.

A small commonMain test now demonstrates the essential mechanism without adopting the frontend code or Compose:

```kotlin
data class View(val visible: Int, val background: String)

val visible = Memory(1)
val background = Memory("idle")

val composite: Storage<View> = Storage(
    read = { View(visible.read(), background.read()) },
    write = { next ->
        if (visible.read() != next.visible) visible.write(next.visible)
        if (background.read() != next.background) background.write(next.background)
    }
)
```

This is an **example assembled from callbacks**, not yet a new `CompositeStorage` API. It shows why `Storage` must remain a small interface with `read` and `write`, not an implementation containing precisely one Source.

Formally, let the backing state be `M = M₁ × ⋯ × Mₙ` and the public view be `V`. We want a view function `assemble : M → V` and an update router `route : V → M → M`. If the pair obeys the appropriate get/set laws, it is lens-like; **we must not assume those laws before checking simultaneous changes and mutation semantics**. For an arbitrary router, the weaker Selection viewpoint remains available.

Compose-observable memory versus plain memory affects **which writes invalidate composition**, not the type of the outer Storage. Combining them behind one read/write interface is allowed. The outer read may nevertheless establish snapshot dependencies on whichever Compose state values it reads during composition; this is not per-field observation magic.

## Observable and non-observable memory are different implementations

The **same `Storage<T>` interface** may be backed by:

1. **Plain memory** (`Memory(initial)`): stores a Kotlin value in a field; does not participate in Compose snapshot observation. Useful for tests, process registries, non-UI context and state that should not trigger UI invalidation.
2. **Compose `MutableState<T>`**: reads/writes `state.value`; Compose can record reads and invalidate scopes which read that state when its value changes, subject to the snapshot mutation policy. This needs Compose runtime and must **not** be added to MathCat's platform-independent core.
3. **Other stores**: browser/session storage, databases, key/value stores, application services, etc. Synchronous callbacks fit this interface only when their operation really is synchronous; asynchronous persistence needs a distinct `suspend`-capable abstraction.

### Future Compose adapter (illustration only, NOT implemented in MathCat core)

```kotlin
@Composable
fun <T> rememberPlainStorage(initial: () -> T): Storage<T> =
    remember { Memory(initial()) }

@Composable
fun <T> rememberObservableStorage(initial: () -> T): Storage<T> {
    val state = remember { mutableStateOf(initial()) }
    return remember(state) {
        Storage(
            read = { state.value },
            write = { state.value = it }
        )
    }
}
```

These functions are **pseudocode candidates** for a separate Compose-runtime adapter module. They deliberately have different names. A `@Composable` function may return plain Memory; being created inside Composition does not make storage observable. `remember` preserves identity across recompositions at its call site; it does not make a normal mutable property a snapshot state.

If Compose evaluates a Source that reads a `MutableState<T>.value` **during composition**, the read can become a dependency of the current recomposition scope. Reading a plain Memory Source does not register such a dependency. Not every write triggers a whole-page rerender: invalidation is related to *which state was read*, equality policy and recomposition scopes. This is not a universal guarantee about arbitrary code inside callbacks.

For a large application, observing one monolithic `mutableStateOf<Application>` may still invalidate a broad set of consumers; splitting intentional UI-facing slices into independent observable state holders can improve granularity. Non-UI context/process data may remain plain memory.

### Findings from the existing Solyton frontend (read-only)

[Application Storage](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/storage.kt) currently distinguishes:

- `application` and `modals`: values created using `remember { mutableStateOf(...) }`; they are observable.
- `context`: ordinary `var context = Context()`; not a Compose snapshot value.
- `processes`: ordinary `val processes = Processes()`; registry updates are intentionally not snapshot writes.

**Potential lifecycle issue:** `context` and `processes` are created **inside a @Composable function but outside `remember`**. If that function is executed again, they can be recreated and the active state lost. A remembered plain Memory or remembered ordinary holder could preserve their identity without making data changes observable. The exact real-world impact depends on how the Composable is called/retained.

**Potential update issue:** the application writer uses an exclusive `when` to select which kind of difference to process. When an incoming value differs in *multiple* managed components, only its first matching branch is applied; the others may not be updated. Compare the *actual* combined value returned by `read()` with the requested write in a future regression test. The existing `onChange` wrapper compares old versus proposed value **before** routing the write, then starts a callback with the proposed `newApplication`, which can differ from the effective post-write value. This deserves focused tests before any Solyton changes.

**Potential identity/law issue:** `Application.withProcesses` modifies `this.processes.registry` in place while `Processes.registry` is a mutable map. This is intentionally non-reactive, but it means the usual immutable-copy intuition and any claimed Lens laws need additional scrutiny. The writer also distinguishes `modals` and `processes` using reference inequality (`!==`) but `context` with value inequality (`!=`).

**Potential lifecycle/effects issue:** the frontend's `onDispatch` starts `CoroutineScope(Job()).launch` each time it is called. If the `@Composable Storage()` factory is re-entered during recomposition, multiple collectors could be started without a composition-tied cancellation owner. The code was inspected, but duplicate collectors in a running application have not been reproduced. Future Compose integration should test lifecycle and cancellation explicitly.

Neither issue is changed by this MathCat PR; they are observations and a follow-up suggestion, **not a claim that production behavior has already been reproduced**.

## What to test later in the Compose module

- `remember` preserves the identity of an unobserved `Memory<T>` after recomposition.
- A value read from plain memory does not cause recomposition merely because it changes.
- A Compose `MutableState<T>`-backed Source **does** participate in snapshot observation when read within composition.
- A focused `Storage * Lens` remains compatible with both backends.
- Mixed observed and unobserved state does not discard changes to either during one composite write.
- Recomposition and effects (`onInit`, `onDispatch`) do not accidentally start duplicate listeners.

## Implementation status

- `Source<T> = Reader<Unit,T>`: implemented in `mathcat-core/src/commonMain/.../reader/Source.kt`.
- `Storage<T>` interface with primary `read()/write()`, derived `source()`, and standalone factories: implemented in `mathcat-core/src/commonMain/.../storage/Storage.kt`.
- `Memory<T>`: implemented as a plain, non-observable backend in `mathcat-core/src/commonMain/.../storage/Memory.kt`.
- Common tests cover live Source reads, memory writes, independent Memories behind one Storage, and Lens/Selection/Reader lifting; CI must establish which platform tasks have passed.
- Compose adapter: **not implemented**. Putting Compose runtime into commonMain would compromise the minimal math core.
- Recomposition and persistence are **not** contractual properties of plain `Storage<T>`.

**Decision, 2026-11-18:** Retain the author's `read/write` API. The Solyton FE Storage was **inspected only** and is **not ported**, reimplemented, or refactored in this PR. Generalized composite routing, reactive adapters, event hooks and state lifecycle need a separate review and explicit tests before any implementation.
