# MathCat — Mathematical foundations

**Status:** design and implementation reference · 18 November 2026 · **Draft PR #2**  
**Audience:** Kotlin developers first; categorical structure made explicit for those who want to follow it.  
**Scope:** the **implemented** `mathcat-core`. The separate `mathcat-type-algebra` module models type expressions as data and does **not** extend Kotlin's kind system.

> **Principle:** Every mathematical name should correspond to an operation, a type signature, a law, or an explicit limitation. The goal is not mathematical decoration; it is to make compositions predictable.

## 1. Types, arrows, and composition

For this pure core we work informally in the category **Set**:

- objects: value types `A`, `B`, `S`;
- arrows: pure functions `f : A → B`;
- identity: `id_A : A → A`;
- composition: `g ∘ f : A → C` for `f : A → B`, `g : B → C`.

The Kotlin function types are an implementation model, not an enforcement of purity or totality. Exceptions, mutation and divergent functions require care when claiming an observational law.

### Reader = an arrow

```text
Reader<S,A> ≅ Hom(S,A)
Reader<S,A> * Reader<A,B> → Reader<S,B>
(f * g)(s) = g(f(s)) = (g ∘ f)(s)
```

`Reader<S,->` is covariant in its result parameter via `map`. The identity and associativity of `*` follow from ordinary function composition.

```kotlin
val name: Reader<Person, String> = Reader { it.name }
val size: Reader<String, Int> = Reader { it.length }
val nameLength: Reader<Person, Int> = name * size
```

## 2. Writer = a command-indexed endomorphism, not the Writer monad

MathCat retains the Evoleq name **`Writer`** intentionally:

```text
End(S)        = Hom(S,S)
Writer<S,A>   ≅ Hom(A,End(S))
              ≅ A → (S → S)
```

Every input `a : A` selects a state transformation `S → S`. `End(S)` itself is a monoid under function composition, with `id_S` as its unit.

This `Writer` is **not** the usual functional-programming Writer monad `(A, Log)`.

### Why `contraMap` is mathematically appropriate

Given `w : Writer<S,A>` and `h : B → A`:

```text
w.contraMap(h) : Writer<S,B>
w.contraMap(h)(b)(s) = w(h(b))(s)
```

It is **precomposition in the command type**. Thus `Writer<S,->` is contravariant in its command parameter:

```text
w.contraMap(id) = w
w.contraMap(f).contraMap(g) = w.contraMap(f ∘ g)
```

This is why a backend response can be mapped into a local update without the Action knowing how the surrounding state is structured.

```kotlin
data class ApiResult(val newCount: Int)
val countWriter = Writer<Int, Int> { value -> { _ -> value } }
val fromApi: Writer<Int, ApiResult> =
    countWriter.contraMap { result: ApiResult -> result.newCount }
```

## 3. Lens and Selection — same operations, different laws

A `Selection<S,A>` consists of:

```text
get : S → A
set : A → (S → S)
```

Equivalently, by currying, `S → A × (A → S)`: a focus and a way to rebuild the source for each replacement. This is the shape of a **Store**-comonad coalgebra candidate; not every pair of operations is a lawful coalgebra.

A `Lens<S,A>` has the same operational signature but **promises** the three well-behaved lens laws for every applicable `s,a,b`:

```text
Get–Put   set(get(s))(s)          = s
Put–Get   get(set(a)(s))          = a
Put–Put   set(b)(set(a)(s))       = set(b)(s)
```

The laws are **contracts**; the Kotlin constructor cannot prove them. We only call a compositional result a `Lens` when both components are assumed lawful.

### Why Selection is useful

For `filterBy(even)`:

```text
get([1,2,3])            = [2]
set([2])([1,2,3])       = [1,3,2] ≠ [1,2,3]
```

The original value changes even when we write back what we just read: **Get–Put fails**. It is still a perfectly meaningful `Selection` with a defined operational composition.

### Composition and lawful boundaries

Let `l : Selection<S,A>`, `r : Selection<A,B>`. Define:

```text
(r ∘ l).get(s)    = r.get(l.get(s))
(r ∘ l).set(b)(s) = l.set(r.set(b)(l.get(s)))(s)
```

This composition is associative as an operation on read/write pairs, without invoking Lens laws. The identity is the pair `get=id`, `set(a)(s)=a`. Lawful Lenses form a compositional substructure, closed under identity and composition.

It follows that:

```text
Lens * Lens           → Lens
Lens * Selection      → Selection
Selection * Lens      → Selection
Selection * Selection → Selection
```

A lawful outer Lens cannot make an unlawful inner Selection lawful by wishful typing.

### Lift a command into a bigger state

For `l : Selection<S,A>` and `w : Writer<A,B>`:

```text
(l * w)(b)(s) = l.set(w(b)(l.get(s)))(s)
```

This is the precise formula behind the frequently useful **Lens × Writer × contraMap** pattern:

```text
API response --contraMap--> domain command
                                  |
                                  v
whole S --Lens.get--> focused A --Writer--> updated A --Lens.set--> updated S
```

The diagram describes **one transformation**, not a claim that effects or concurrent mutations are atomic.

## 4. State — a monad with its multiplication visible

Fix a state type `S`. The state type constructor is:

```text
T_S(A) = S → (A × S)
```

This is a functor in `A`. MathCat's names deliberately expose the **unit** and **multiplication**, rather than hiding the latter behind a single conventional `flatMap` name.

### Unit: `Return`

```text
η_A : A → T_S(A)
η_A(a)(s) = (a,s)
```

Corresponding code:

```kotlin
State.Return<S,A>(a)
```

`Return` acts on the **type constructor's structure**, not on an already existing state value. The Kotlin companion is a practical home for this operation until higher-kinded type abstractions are available.

### Multiplication: `multiply`

```text
μ_A : T_S(T_S(A)) → T_S(A)

μ_A(outer)(s) =
    let (inner,s1) = outer(s)
    in  inner(s1)
```

The **updated state `s1`** is passed to the inner computation. This is the key semantic content of multiplication.

### Kleisli extension: `bind`

For `f : A → T_S(B)`:

```text
m.bind(f) = μ_B(T_S(f)(m))
          = m.map(f).multiply()
```

That identity is implemented directly in MathCat rather than merely documented.

A Kleisli arrow is named according to existing Evoleq vocabulary:

```kotlin
typealias KlState<S, A, B> = (A) -> State<S, B>
```

Mathematically it is `A → T_S(B)`. Two such arrows compose via `bind`, with `Return` as Kleisli identity.

### State updates that preserve the carried value

An update is often intended to change **only** the state and leave the pipeline value unchanged:

```text
update(u) : A → T_S(A)
update(u)(a)(s) = (a, u(s))
```

```kotlin
val increment: KlState<Int, String, String> = State.update { it + 1 }
val initial = State.Return<Int, String>("request-id")
val result = (initial * increment)(10)
// result == ("request-id" to 11)
```

Using `Unit` in place of `A` would **discard the value**, so that is not the default `State.update` signature. This decision came from the existing practical Evoleq/Kleisli use case, not from a desire to invent a new abstraction.

### Monad laws

For all `m : T_S(A)`, `a : A` and appropriately typed `f,g`:

```text
Left identity:   Return(a).bind(f)           = f(a)
Right identity:  m.bind(Return)              = m
Associativity:   m.bind(f).bind(g)           = m.bind { a → f(a).bind(g) }
```

Equivalently, in terms of unit and multiplication:

```text
μ ∘ η_T  = id_T
μ ∘ Tη   = id_T
μ ∘ μ_T  = μ ∘ Tμ
```

The present test suite checks representative equations on JVM and JS. It does **not** mechanically prove them for every Kotlin function or arbitrary side effects.

## 5. Identity functors, functor composition, higher kinds

The desired *type-level* mathematics is separate from the already implemented operations on values.

```text
Id(A)                = A
(F ∘ G)(A)           = F(G(A))
(F ⊕ G)(A)           = F(A) + G(A)
(F ⊗ G)(A)           = F(A) × G(A)
```

A genuine `Functor<F>` should abstract over a constructor `F : Type → Type`, and `Nat<F,G>` over a natural family `F(A) → G(A)`. Kotlin today does not have general native higher-kinded parameters. The sibling `mathcat-type-algebra` module therefore models syntax trees for these expressions; **it does not compute Kotlin types or prove naturality**.

Functor composition is automatic given suitable functors; Monad composition is **not** generally automatic and may require a distributive law. This distinction is fundamental to the later language/plugin research.

## 6. From mathematical structure to an approachable API

| Mathematical concept | Public Kotlin spelling | Where it is used |
| --- | --- | --- |
| Function / morphism | `Reader` | Observations and compositional projections |
| Precomposition, contravariance | `Writer.contraMap` | API payload → domain writer command |
| Store coalgebra laws | `Lens.get`, `Lens.set` | Lawful focusing and rebuilding |
| Read/write without lens laws | `Selection` | Filtering and application-specific selections |
| Monad unit `η` | `State.Return` | Introduce a value without changing state |
| Monad multiplication `μ` | `State.multiply` | Flatten nested state computations |
| Kleisli extension | `State.bind` | Chain computations with state threading |
| Kleisli arrow | `KlState` | Input value + stateful computation |
| Value-preserving state effect | `State.update` | Change state without discarding input |
| Functor / kind operators | `mathcat-type-algebra` experiment | Explicit research, not native Kotlin HKT |

**Vocabulary policy:** Keep the author's `Return`, `multiply`, `map`, `bind`, `get`, `KlState`, `Reader`, `Writer`. Do not create redundant `pure`/`flatMap`/`modify` public aliases. Mathematical symbols live in explanations and equations; Kotlin code keeps a conflict-aware vocabulary.

**Boundaries:** No `suspend` in the pure core; IO, Storage, atomicity and cancellation are separately designed effect concerns. All laws mean extensional behavior under the stated pure-function assumptions, not object identity or a statement about arbitrary observable side effects.
