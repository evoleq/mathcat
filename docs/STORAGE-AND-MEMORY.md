# Storage, Memory and Compose boundaries

**MathCat 2.0 · 2026-11-18 · Draft PR #2.** An implementation and architectural note, not an automatic migration of existing Solyton code.

## A simple algebraic boundary

```text
Source<T>  = Reader<Unit,T>              (read only; deferred evaluation)
Storage<T> = Source<T> + (T -> Unit)      (read + write)
Memory<T>  implements Storage<T>         (plain mutable in-memory backing)
```

The notation `+` on the Storage line means **a record of capabilities**, not the coproduct/sum type of the type-algebra project.

The actual API is:

```kotlin
interface Storage<S> {
    val source: Source<S>
    fun write(value: S)
    fun read(): S = source.emit()
}

class Memory<S>(initial: S) : Storage<S>
```

`Source<T>` is **already present in legacy Evoleq**, and is now restored to MathCat as exactly `typealias Source<T> = Reader<Unit,T>`. It is a typealias rather than a wrapper, so `Source * Reader` is ordinary Reader composition. `Storage<T>.source` exposes a **live, lazily evaluated** read; previously obtained Source values observe later writes, not a frozen snapshot.

The named `Storage(source, write)` and `Storage(read, write)` factory functions make adapters easy without subclassing.

### Composition

```text
Storage<S> * Reader<S,A>        → Source<A>
Storage<S> * Lens<S,A>          → Storage<A>
Storage<S> * Selection<S,A>     → Storage<A>   (no Lens-law guarantee)
```

For a lawful Lens `l : S → A` and `storage : Storage<S>`:

```text
(storage * l).source() = l.get(storage.read())
(storage * l).write(a) = storage.write(l.set(a)(storage.read()))
```

The notation `source()` in the equation means evaluation of the `Source` (i.e. `emit()`), not the name of a method on `Storage`.

**Important:** A focused write executes a read–modify–write sequence which is **not atomic**. Concurrent writers require a stronger transactional/atomic storage abstraction later. Plain `Memory` is not thread-safe.

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
            source = Source { state.value },
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

**Potential update issue:** the application writer uses an exclusive `when` to select which kind of difference to process. When an incoming value differs in *multiple* managed components, only its first matching branch is applied; the others may not be updated. This deserves focused regression tests before any Solyton changes.

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
- `Storage<T>` interface with Source projection and standalone factories: implemented in `mathcat-core/src/commonMain/.../storage/Storage.kt`.
- `Memory<T>`: implemented as a plain, non-observable backend in `mathcat-core/src/commonMain/.../storage/Memory.kt`.
- Common tests cover live Source reads, memory writes and Lens/Selection/Reader lifting; CI must establish which platform tasks have passed.
- Compose adapter: **not implemented**. Putting Compose runtime into commonMain would compromise the minimal math core.
- Recomposition and persistence are **not** contractual properties of plain `Storage<T>`.
