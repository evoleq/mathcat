# MathCat 2.0 — Core v0 (implementation proposal)

**Date:** 2026-11-18  
**Status:** draft implementation / review, NOT released or merged  
**Scope:** `mathcat-core` Kotlin Multiplatform (JVM and JavaScript/Node.js)  
**Parent research PR:** [MathCat PR #1](https://github.com/evoleq/mathcat/pull/1)  
**Related:** [Evoleq 2.0 exploration](https://github.com/doctor-smith/ai-command/issues/44), [legacy bugfix draft #394](https://github.com/doctor-smith/solawi-bid/pull/394)

## Objective

Implement the smallest actually usable algebra of compositional operations without copying the full legacy Evoleq collection. Preserve the useful frontend pattern:

```kotlin
val writer: Writer<Dashboard, ApiResponse> =
    entriesLens
        .upsertAllBy(Entry::id)
        .contraMap { response: ApiResponse -> response.entries }
```

The Lens selects **where**; the keyed Writer chooses **how**; `contraMap` translates an API response to the Writer's input. The Action/effect layer can consume the result later.

## Public primitives and guarantees

| Primitive | Meaning | Laws / guarantees | Suspends? |
| --- | --- | --- | --- |
| `Reader<S,A>` | `S → A`, nominal `fun interface` | function composition has identity and associativity for pure functions | No |
| `Writer<S,A>` | `A → (S → S)`, nominal `fun interface` | `contraMap` preserves identity/composition for pure functions | No |
| `Selection<S,A>` | Reader + Writer | supports composition; **no Lens laws assumed** | No |
| `Lens<S,A>` | Reader + Writer **subject to laws** | Get–Put, Put–Get, Put–Put are obligations of implementers | No |
| `State<S,A>` | `S → (A,S)` | `Return`/`map`/`multiply`/`bind` satisfy the State Monad laws for pure functions | No |

`Reader` and `Writer` are intentionally **nominal**, not bare function typealiases: overloads `Selection * Reader` and `Selection * Writer` can otherwise collide in the JVM signature and/or lead to ambiguous inference. `fun interface` preserves lambda construction with explicit type inference and minimal wrappers. The API trade-off (nominal versus aliases) is **proposed**, not final.

The name `Writer` preserves the vocabulary from Solyton/Evoleq but here means **state updater**, *not* the conventional Writer monad. Keep the existing name for developer experience; do not rename it to `Updater` without an explicit request.

### Lens laws

Given `l: Lens<S,A>`, `s: S` and `a,b: A`:

```text
Get–Put: l.set(l.get(s))(s)       == s
Put–Get: l.get(l.set(a)(s))       == a
Put–Put: l.set(b)(l.set(a)(s))    == l.set(b)(s)
```

A constructor accepting arbitrary lambdas does **not** establish these laws at compile time. Tests show representative examples; a complete mathematical proof for arbitrary user-supplied `Lens` is not claimed. A `Lens * Lens` is lawful provided both inputs are lawful.

### Selections

`FilterBy(predicate): Selection<List<A>,List<A>>` preserves the historic filter-and-append semantics:

```text
get([1,2,3])                 = [2]       (select even)
set([2])([1,2,3])            = [1,3,2]
```

This is deliberately **not** a Lens because `set(get(s))(s) ≠ s` in general. It still composes with Lens, Selection, Reader and Writer. `FirstBy` is not ported as a total Lens; its matching focus is partial, and replacement must maintain the predicate if lens laws are desired.

### Composition

| Left | Right | Result |
| --- | --- | --- |
| `Reader<S,A>` | `Reader<A,B>` | `Reader<S,B>` |
| `Lens<S,A>` | `Lens<A,B>` | `Lens<S,B>` |
| `Lens<S,A>` | `Selection<A,B>` | `Selection<S,B>` |
| `Selection<S,A>` | `Lens<A,B>` | `Selection<S,B>` |
| `Selection<S,A>` | `Selection<A,B>` | `Selection<S,B>` |
| `Lens/Selection<S,A>` | `Reader<A,B>` | `Reader<S,B>` |
| `Lens/Selection<S,A>` | `Writer<A,B>` | `Writer<S,B>` |
| `Writer<S,A>` | `contraMap(B→A)` | `Writer<S,B>` |
| `State<S,A>` | `KlState<S,A,B> = (A) → State<S,B>` | `State<S,B>` via `bind` / `*` |

The first operator denotes `*` for most rows; `contraMap` is a named method. Readers and Writers are nominal so that the lifted `*` overloads have distinct JVM signatures. Named `timesR` and `timesW` also exist for clarity.

For pure inputs, the composed Selection formulas are:

```text
(a*b).get(s) = b.get(a.get(s))
(a*b).set(v)(s) = a.set(b.set(v)(a.get(s)))(s)
```

Pure extensional equality, not equality of function object identities, is the intended notion. Effects inside supplied lambdas invalidate some algebraic assumptions; no purity enforcement is claimed.

## State vocabulary and value-preserving state updates

The author deliberately uses the existing **Evoleq vocabulary**: standalone `Return`, standalone `Update`, standalone `Get`, `map`, `multiply`, `bind`, `KlState` and the lowercase `get` property on optics. It reduces accidental declaration clashes and gives the algebra a consistent set of names. **Do not introduce public `pure`, `flatMap` or `modify` synonyms** without a separate API decision.

```kotlin
typealias KlState<S, A, B> = (A) -> State<S, B>

// Monad multiplication: State<S, State<S, A>> -> State<S, A>
val joined = nested.multiply()

// Value is retained, while the state changes:
val change: KlState<Int, String, String> = Update { it + 3 }
val result = (Return<Int, String>("payload") * change)(5)
// result == ("payload" to 8)
```

The top-level, capitalized `Update` is intentionally a **Kleisli constructor**, not a method on the `State` companion object. The naming follows existing Evoleq conventions and allows simple import/composition without carrying `State.` throughout the DSL.

The important distinction is between **changing the state** and **discarding the value**. Returning `Unit` is appropriate for an intentionally value-less computation, but not as the default helper for an operation that is meant to be chained. Here the primitive is a `KlState<S,A,A>`: it forwards its input `A` unchanged while updating `S`.

`multiply` threads the **outer successor state** into the inner computation, and `bind` is defined as `map(kleisli).multiply()`. This also makes the connection to the monad multiplication `μ` explicit. The alias `KlState` remains an ordinary function type (no unsupported Higher Kinds claimed).

### Construction, composition and import aliases

MathCat follows Evoleq's deliberate developer-experience convention: **standalone constructors** read as mathematical names (for example `Return`, `Update`, `IdReader`, `IdLens`, `FilterBy`, `Append`, `AppendAll`, `UpdateAllBy`, `UpsertAllBy`), while **operations on existing objects** are named `map`, `multiply`, `bind`, `get`, `set`, `contraMap` and composed using `*`. Natural type constructors such as `Reader { ... }`, `Writer { ... }` and `Lens(...)` keep their existing type names; no redundant factories.

Receiver-based convenience functions such as `entriesLens.upsertAllBy(key)` remain lowercase, because the receiver carries the context. Do not force capitalization where it makes the DSL less fluid.

```kotlin
val increment: KlState<Int, String, String> = Update { it + 1 }
val run = Return<Int, String>("payload") * increment
```

**Get and Set as constructions.** The existing Evoleq pattern is retained:

```kotlin
Get(lens)       // Reader<S,A>; exactly lens.get
Set(lens)       // Writer<S,A>; exactly lens.set
Get(selection)  // Reader<S,A>; no lawfulness claim
Set(selection)  // Writer<S,A>; no lawfulness claim
Get<S>()        // State<S,S>: returns the current state without changing it
```

The Optics' `get` and `set` remain lowercase properties. `Get(lens)` constructs a Reader that can be composed independently; `Get<S>()` constructs a State computation. This is deliberate overload by **argument shape**, not return type.

The older Evoleq also defines `Get(key)` and `GetForSure(key)` for maps. MathCat preserves these in `org.evoleq.mathcat.core.maps`, separate from the optics package:

```kotlin
import org.evoleq.mathcat.core.Get
import org.evoleq.mathcat.core.maps.Get as MapGet
import org.evoleq.mathcat.core.maps.GetForSure
```

`MapGet(key)` returns a nullable map value; `GetForSure(key)` is **partial** and fails when there is no non-null value. The `Get(key)` compatibility constructor treats a null key as no selection, matching Evoleq's existing helper.

**Future monad modules** may each export their own `Return` (e.g., a hypothetical `org.evoleq.mathcat.result.Return`). Kotlin consumers can use import aliases:

```kotlin
import org.evoleq.mathcat.core.Return as StateReturn
// hypothetical future module, NOT currently implemented:
// import org.evoleq.mathcat.result.Return as ResultReturn
```

Import aliases disambiguate **separate importable declarations**; they cannot resolve same-package conflicting signatures or two functions distinguishable only by return type. Therefore constructors for different structures should live in distinct packages/modules with deliberate signatures. We do not invent wrapper names such as `pure` merely to avoid imports.

## Collection writers: preserve the pattern, tighten contracts

The new keyed list writers are **additional initial examples**, not drop-in replacements for the older arbitrary-predicate update methods.

- `Append(): Writer<List<A>,A>`: append a single element.
- `AppendAll(): Writer<List<A>,List<A>>`: append several elements.
- `UpdateAllBy(keyOf)`: replace existing entries for matching keys; ignore unknown updates.
- `UpsertAllBy(keyOf)`: replace existing entries in position and append unknown keys **in incoming order**.
- `Lens<S,List<A>>.updateAllBy(keyOf)`, `.upsertAllBy(keyOf)`, `.add()`: facade over `Lens * Writer`.

**Explicit duplicate policy:** duplicate keys in the source or update batch throw `IllegalArgumentException`, rather than hiding conflicts by first-match resolution, dropping updates or using inconsistent `equals`-based append behavior. Ordering of pre-existing items is preserved. Lookup is hash-backed and expected linear average-case; performance is not benchmarked.

**Caveat:** Full business-level conflict handling (version checks, concurrent updates, transactions, optimistic locking) is NOT addressed by these pure collection writers.

## Suspend and platform policy

All operations here are *pure value transformations* and hence not `suspend`. An effects layer may later define `SuspendState<S,A>`, `Storage<A>`, `SuspendStorage<A>`, atomic update abstractions, Ktor adapters and platform-dependent operations. Construction of a suspend-capable computation need not itself be suspend. Storage and IO are intentionally **not** in `mathcat-core`.

`mathcat-core` uses Kotlin standard library only. It shares the Kotlin Multiplatform targets with the type-algebra spike (JVM and JS/Node.js). Neither depends on KSP or `fp-axioms`; generator plugins are separate JVM-hosted build tools.

## Validation plan (implemented commonTest suite)

- Reader identity/associativity, `map`; Writer `contraMap` identity and composition.
- All three Lens laws (representative data models), Lens composition identity/associativity.
- Lens × Reader / Writer; Selection × Lens / Selection / Reader / Writer.
- Explicit non-lawful `FilterBy` regression.
- State `Return`, `map`, `multiply`, `bind`, `Get`, `KlState` and Monad identities/associativity for representative state functions.
- Kleisli state update: `Update(change): KlState<S,A,A>` changes state while passing the value through; no implicit `Unit` result or `modify` API.
- Keyed `updateAll`, `upsertAll`, no-match behavior, duplicate rejection, ordering.
- Solyton-style Lens-lifted Writer with response conversion through `contraMap`.

**Compilation and execution:** [GitHub Actions run #44265321148](https://github.com/evoleq/mathcat/actions/runs/44265321148) completed **successfully**, running `:mathcat-core:jvmTest`, `:mathcat-core:jsNodeTest` and both existing type-algebra targets. This validates the tested JVM/JS implementations; it does not constitute formal proof of all laws or compatibility with more platforms.

## Explicit exclusions and follow-up

- No HKT type syntax, general typeclass system, natural-transformation API or compiler modification here.
- No `@Lensify` KSP changes; read-only optics and KSP warnings need a separate verified fix.
- No automatic rewrite/migration of Solyton or its helpers.
- No publishing choice (JitPack/Maven Central), artifact coordinates, license or release has been finalized.
- Next candidate slices: **effects/Storage**, optional `SuspendState` and cancellation-safe helpers; structured lens-law testing helpers; keyed conflict policies if application evidence warrants them.

## Work log

| Date | Work | Status |
| --- | --- | --- |
| 2026-11-18 | MathCat type-algebra research and green JVM+JS baseline | [Parent draft PR #1](https://github.com/evoleq/mathcat/pull/1), not merged |
| 2026-11-18 | Dedicated `mathcat-core` module using nominal Reader/Writer | Draft implementation |
| 2026-11-18 | Selection and Lens laws separated; FilterBy represented honestly | Draft implementation + tests |
| 2026-11-18 | Pure State and keyed list Writer examples added | Draft implementation + tests |
| 2026-11-18 | CI expanded for two modules × two platforms | [Run #44265321148: SUCCESS](https://github.com/evoleq/mathcat/actions/runs/44265321148) |
| 2026-11-18 | Author's naming and value-preserving Kleisli state update applied | Standalone `Return`, `Update`; lowercase `multiply`, `bind`, `map`, `get` |
| 2026-11-18 | Consistent names for top-level constructors | `IdReader`, `IdLens`, `FilterBy`, `Append`, `AppendAll`, `UpdateAllBy`, `UpsertAllBy`; CI retest pending |
| 2026-11-18 | Restore Evoleq Get/Set constructors and map Get variants | `Get(lens/selection)`, `Set(lens/selection)`, `Get<S>()`, `maps.Get(key)`, `maps.GetForSure(key)` + import-alias tests |
