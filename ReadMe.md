# MathCat

**Mathematically flavored functional programming for Kotlin Multiplatform.**

MathCat is being reworked as a **small, law-aware foundation** for compositional software. The first step is a research prototype for **algebraic type operations**, not a claim that the Kotlin language already supports higher-kinded type parameters.

## MathCat Core (new draft module)

The experimental [`mathcat-core`](mathcat-core) module introduces small, nominal, lambda-friendly `Reader` and `Writer` types; **lawful** `Lens` contracts; composable but **not necessarily lawful** `Selection`; and a pure, non-`suspend` State monad.

The central composition patterns are `Lens * Lens`, `Lens * Selection`, `Lens * Reader`, `Lens * Writer`, and `Writer.contraMap`. Keyed collection Writers `updateAllBy` and `upsertAllBy` illustrate the same production-proven idea from Solyton's frontend Actions without copying the old library's ambiguous duplicate-handling semantics.

The **mathematical origins are part of the API design**: [Mathematical foundations](docs/MATHEMATICAL-FOUNDATIONS.md) explains function composition, Writer contravariance, Lens/Selection laws, State's monad multiplication and Kleisli arrows. In particular, the capitalized top-level `Update` constructor produces a `KlState` that changes state while preserving the carried value.

The public DSL now follows a simple rule: **constructors** `Return`, `Update`, `IdReader`, `IdLens`, `FilterBy`, `Append`, `AppendAll`, `UpdateAllBy` and `UpsertAllBy` are capitalized and can be used directly; **operations** `map`, `multiply`, `bind`, `get`, `set` and `contraMap` stay lowercase. Future monads may reuse the name `Return` in their own packages; Kotlin's `import ... as ...` syntax disambiguates call sites without changing mathematical vocabulary. A [commonTest import-alias smoke test](mathcat-core/src/commonTest/kotlin/org/evoleq/mathcat/consumer/ConstructorImportsTest.kt) verifies the public core's alias ergonomics.

In particular, **`Get(lens)` and `Set(lens)`** turn an optic into the corresponding `Reader` and `Writer`; `Get<S>()` constructs a pure state-read computation. Map readers use `org.evoleq.mathcat.core.reader.maps.Get(key)` / `GetForSure(key)`; explicit imports or import aliases keep these meanings clear. A `State` is directly callable (`state(s)`) without importing a separate invocation extension. Packages now keep the standalone constructors unambiguous: `core.state`, `core.reader`, `core.writer`, `core.optics`, `core.collections` and `core.reader.maps`.

### Storage and Memory

`Source<T> = Reader<Unit,T>` is restored from legacy Evoleq. The `storage` package now provides a synchronous `Storage<T>` interface with a first-class `source: Source<T>`, plus a plain `Memory(initial)` backend. `Storage * Reader` yields a live Source, while `Storage * Lens` and `Storage * Selection` yield focused Storage handles.

**Memory is not Compose-observable.** A future Compose adapter can back the **same** Storage interface with `MutableState<T>`, allowing the caller to decide which data should participate in snapshot-driven recomposition. The core intentionally has **no Compose dependency**. See [Storage, Memory and Compose boundaries](docs/STORAGE-AND-MEMORY.md), including observations from Solyton's existing mixed observable/non-observable frontend Storage.

See [Core architecture, laws and tests](docs/CORE-ARCHITECTURE.md). This is still a **draft proposal**: there is no stable API, release, or publishing decision.

## MathCat 2.0 (research prototype)

The repository also contains an experimental `mathcat-type-algebra` Kotlin Multiplatform module, sharing the targets described in [Multiplatform support](docs/MULTIPLATFORM.md):

- a value-level **AST** for unary type constructors, identity, composition and pointwise sum/product;
- tagged `Sum2<A,B>` and `Product2<A,B>`, with explicit distributivity/factorization isomorphisms;
- common tests used by independently configured JVM, JS, Native and WebAssembly targets; Android and Apple cross-compilation are checked separately.

**Important:** These are normal Kotlin types and transformations. They are *not* built-in sum/product syntax, an HKT system, a compiler plugin or a proof of the laws.

See [Architecture and research notes](docs/ARCHITECTURE.md) for the mathematical definitions, limits, next steps and the relationship to [fp-axioms](https://github.com/evoleq/fp-axioms).

### Build and test locally

With JDK 17+ and Gradle 9.2.1 available:

```sh
gradle :mathcat-type-algebra:jvmTest :mathcat-type-algebra:jsNodeTest \
       :mathcat-core:jvmTest :mathcat-core:jsNodeTest
```

For the JS tests, Kotlin/Gradle downloads the required Node.js environment when necessary. Gradle is pinned in CI; a Gradle wrapper can be added after the initial build has been verified.

**Platform targets:** Both modules declare JVM, JavaScript/Node.js, WebAssembly/Node.js, Android, Linux, Windows, iOS and macOS. The [platform matrix](docs/MULTIPLATFORM.md) specifies exact architectures, host requirements and what CI really verifies. **CI builds and tests only; it does not publish**. JitPack versus Maven Central and the final license remain open decisions.

## Earlier MathCat experiments

The earlier MathCat libraries remain separate and are **not** implied dependencies of the new module. They are useful historical references; their API quality and contracts are being reassessed before any potential migration.

- [mathcat-core](https://github.com/evoleq/mathcat-core), [mathcat-morphism](https://github.com/evoleq/mathcat-morphism), [mathcat-continuation](https://github.com/evoleq/mathcat-continuation)
- [mathcat-reader](https://github.com/evoleq/mathcat-reader), [mathcat-state](https://github.com/evoleq/mathcat-state), [mathcat-store](https://github.com/evoleq/mathcat-store)
- [mathcat-lens](https://github.com/evoleq/mathcat-lens), [mathcat-prism](https://github.com/evoleq/mathcat-prism), [mathcat-profunctor-optics](https://github.com/evoleq/mathcat-profunctor-optics)
- [mathcat-result](https://github.com/evoleq/mathcat-result), [mathcat-structure](https://github.com/evoleq/mathcat-structure), [mathcat-yoneda](https://github.com/evoleq/mathcat-yoneda)

These repositories have **not** been archived or made private. Existing consumers and references should be evaluated before changing their visibility.

## Design principles

- Prefer explicit mathematical contracts over attractive but unverified names.
- Separate the laws an operation promises from whether it can be composed.
- Keep pure operations apart from suspendable execution and platform effects.
- Preserve useful real-world composition patterns such as `Lens * Writer` and `Writer.contraMap`.
- Keep experimental language/tooling research separate from the usable library.

The public API, licensing and release policy for MathCat 2.0 are still under review.
