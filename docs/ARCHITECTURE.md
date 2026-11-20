# ToolKit 2.0 — Architecture and type-operations research

**Status:** research draft, 2026-11-18. No language, library, licensing, or publishing decision is implied by this PR.

## Scope

ToolKit aims for a small, mathematically explicit Kotlin Multiplatform foundation, rather than another collection of framework helpers. Distinguish:

1. **Ordinary Kotlin library:** pure `Reader`, `Writer`/`Updater`, `Selection`, lawful `Lens`, and pure `State`. Proposed, **not implemented here**.
2. **Type-operation semantics:** algebraic sums, products, unary type constructors, identity, composition and explicit conversion between isomorphic types. The initial `toolkit-type-algebra` module implements a **value-level syntax tree** (AST) only.
3. **Tooling:** code generators and optional compiler diagnostics, to be developed separately from the runtime library.
4. **Effects:** storage, suspend operations and platform-specific adapters, also separate from the pure core.

The first Gradle build targeted JVM and JavaScript/Node.js. A subsequent [multiplatform expansion](MULTIPLATFORM.md) also declares Android, WebAssembly and native targets with host-scoped CI. Platform support must only be claimed after successful corresponding jobs. There is **no publishing pipeline**.

## Proposed type algebra (hypothetical syntax, NOT Kotlin)

```text
Kind ::= Type | Kind -> Kind

A, B : Type
F, G : Type -> Type

typefun Id<A>                    = A
typefun Compose<F,G><A>          = F<G<A>>
typefun PointwiseSum<F,G><A>     = F<A> + G<A>
typefun PointwiseProduct<F,G><A> = F<A> × G<A>
```

The implemented AST represents these expressions as values. Its `normalize()` reduces **applications** of identity, composition, pointwise sums/products and constant constructors. It does not turn Kotlin into a language with higher-kinded types; it has no parser or real kind checker.

### Definitional equality versus isomorphism

A sum is **tagged**: `A + A` has distinguishable left/right injections. An untagged union is not the same construct. Product `A × B` contains a pair.

In the intended semantics:

```text
A × Unit                ≅ A
A + Nothing             ≅ A
(A + B) × C             ≅ (A × C) + (B × C)
```

The symbol `≅` means *isomorphic*, not definitionally equal. The library intentionally supplies explicit `distribute` / `factor` functions instead of treating distributivity as an automatic type normalization. Commutativity and reordering are not silently applied.

## Functors, monads, natural transformations

### Functors

The desired higher-kinded interface would look conceptually like:

```kotlin
// NOT valid ordinary Kotlin:
interface Functor<F : Type -> Type> {
    fun <A, B> map(fa: F<A>, f: (A) -> B): F<B>
}
```

Subject to the functor laws, unary endofunctors are closed under composition and have the genuine identity functor `Id(A) = A`. Pointwise sum/product also preserve functoriality over a category admitting the necessary coproducts/products. A wrapper `Id<A>(a)` in ordinary Kotlin is *not* the same thing as definitional identity of type constructors.

### Monads

`pure: A -> F<A>` is an operation associated with the **structure on a type constructor**, not an instance method on an existing `F<A>` value. An explicit Monad dictionary/typeclass can express that more generally than a required companion function.

The signatures of `pure` and `flatMap` do not prove Monad laws. In particular, **two arbitrary monads do not automatically compose to a monad**; additional coherent structure (e.g. a suitable distributive law) is needed. The proposal must not generate an automatic Monad instance for each `Compose<F,G>`.

### Natural transformations

For `η : F ⇒ G`, a family `η_A : F<A> -> G<A>` must satisfy:

```text
G.map(f) ∘ η_A = η_B ∘ F.map(f)
```

for each `f: A -> B`. Identity, vertical composition and whiskering/horizontal composition are candidates for later experiments. Correct method signatures cannot by themselves establish naturality for arbitrary Kotlin implementations.

## Relationship to fp-axioms

`evoleq/fp-axioms` already contains a **KSP** symbol processor, a Gradle plugin that wires KSP, and multiplatform annotation definitions. It is a valuable predecessor, not a dependency of the current ToolKit core.

Read-only code findings (2026-11-18):

- `@Lensify` generates property optics and setter extensions. The existing `@ReadOnly` path emits a `Lens` with a no-op setter; that does **not** satisfy Put–Get in general. A read-only `Reader` would express its contract more accurately.
- `FunctorProcessor` presently detects the existence of companion `lift` by **name** only, without validating its signature. Its absent-lift path generates `TODO()` instead of a symbol-local diagnostic. The intended type-checking helper is not used.
- `MonadProcessor` contains unfinished functions and is not yet a Monad implementation.
- The version catalog pins Kotlin `2.1.0`, KSP `2.1.0-1.0.29` and KotlinPoet `1.16.0`. The Gradle plugin uses `afterEvaluate` and scans `ksp*` configurations. These are candidates for a compatibility audit, **not a confirmed cause of the user's legacy warnings** without actual build logs.

**Tooling options:** pure Kotlin/source generation → KSP → experimental K2 compiler checks → custom language frontend/compiler changes. KSP cannot add arbitrary parser grammar or native higher-kinded type parameters. Compiler plugin APIs are version-sensitive.

## Writer helpers from Solyton: preserve the good composition

Existing frontend actions demonstrate a useful and repeatable decomposition:

```kotlin
// Schematic: exact API names are from existing Evoleq
val writer =
    (applicationLens * collectionLens)
        .updateAll { incoming, stored -> incoming.id == stored.id }
        .contraMap { response: ApiResponse -> response.items.map(::toDomain) }
```

- **Lens** chooses *where*.
- `updateAll` chooses *how* to update the focused collection.
- `contraMap` maps the API result *into the writer's input*.
- The enclosing Action ties the operation to the IO boundary.

This pattern should survive into ToolKit; it does **not** require copying every helper into the algebraic core. A principled future design is **pure list transformation / Writer** lifted through a lawful Lens.

Known review topics in the legacy code:
- `update` has implicit no-match semantics (unchanged list), which should be documented or chosen explicitly.
- `updateAll` scans incoming items per existing item and selects the first matching replacement; complexity and duplicate-key semantics deserve tests.
- `upsertAll` matches replacements using the caller's predicate, but uses `contains`/regular equality to decide what to append. It can insert duplicate business keys if values differ.
- `addMissing` only checks incoming values against the original list, not against other pending additions, so equal incoming keys can both be appended.

**No change to the existing helpers in this PR.** We will first preserve observable use cases with regression tests, then consider naming, policies, data structures and performance optimizations.

## What belongs in commonMain and what needs suspend?

Pure type operations, lenses, collection transformations and function composition are KMP-friendly. Constructing a suspend-capable operation ordinarily need not itself suspend. Actual IO and suspendable operations belong at explicit effect boundaries. `suspend` does not automatically turn blocking work into nonblocking work; cancellation belongs to the execution contract. A KSP or compiler plugin is JVM-hosted tooling even if it generates code for multiplatform libraries.

## Work plan and unresolved questions

1. **Type algebra:** define formal kind checking, variables, definitional equality versus isomorphism and termination of normalization.
2. **Law tests:** identity, composition, sum/product map, naturality and counterexamples. Separate structural checks from mathematical laws.
3. **Runtime core:** Reader/Writer/Selection/Lens and their `*` rules. Keep lawfulness explicit; `Selection` is composable without pretending to be a lawful Lens.
4. **KSP modernization:** isolate fp-axioms warnings with real logs; design proper signature diagnostics rather than generated `TODO()`.
5. **Language experiment:** compare a small source-generating DSL with deeper compiler modifications, without conflating them.
6. **Release:** decide LICENSE (Apache-2.0 proposed; not yet adopted), artifact coordinates and publishing channel **later**. No JitPack/Maven Central decision has been taken.

## Evidence, links and protocol

- Existing project: https://github.com/evoleq/mathcat
- Earlier implementation and real frontend examples: https://github.com/doctor-smith/solawi-bid/tree/main/evoleq
- KSP predecessor: https://github.com/evoleq/fp-axioms
- Solyton fixes (separate draft): https://github.com/doctor-smith/solawi-bid/pull/394
- Research issue: https://github.com/doctor-smith/ai-command/issues/44
- Existing Traversal and trampoline explorations: https://github.com/doctor-smith/ai-command/issues/36 and https://github.com/doctor-smith/ai-command/issues/37
- Kotlin documentation: https://kotlinlang.org/docs/ksp-overview.html and https://kotlinlang.org/docs/custom-compiler-plugins.html

**Change log — 2026-11-18:** Established scope, implemented first-order AST and tagged sum/product representations; introduced JVM/JS tests and CI; inspected fp-axioms and legacy frontend writer helpers; deferred all licensing, publishing, repository privacy and compiler-fork decisions. **The original JVM/JS CI passed; expanded-platform verification is tracked separately in [MULTIPLATFORM.md](MULTIPLATFORM.md).**
