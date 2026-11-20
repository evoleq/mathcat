# Storage composition: products, sums, and embedding existing stores

**ToolKit 2.0 · research design · 18 November 2026 · Draft PR #2**  
**Status:** mathematical/API proposal only. No `x`, sum router, `Absorb`, `Mount`, or generalized composite class is implemented by this document.  
**Existing code:** `core.storage.Storage<T>` with **`read()` and `write()`**, `Memory<T>`, `Storage * Reader/Lens/Selection`, `Storage.source()`.

## Why this matters

Solyton's frontend already exposes a single `Storage<Application>` while actually keeping some state in Compose-observable memory and other state in ordinary, deliberately non-observable memory. It therefore assembles a **view of several existing independent stores**. ToolKit should understand the mathematics without simply turning this particular frontend implementation into the generalized API.

The design goal: **a composite keeps the identity of its constituent Storage handles**. No implicit copying, reinitializing or taking ownership of existing Memories. Reads consult them live; writes have an explicit routing policy. `Memory` is a single backend, not the definition of `Storage`.

## 1. The basic category-theoretic shape

The minimal interface is:

```kotlin
interface Storage<S> {
    fun read(): S
    fun write(value: S)
}
```

The read projection `Storage<S>.source() : Source<S>` is derived (`Source<S> = Reader<Unit,S>`), not an extra required property. A Storage is an **effectful handle**, so normal categorical proofs about pure functions cannot simply be transferred to arbitrary writes, callbacks, mutable shared data or UI observation.

For a composite with backing-memory product `M = M₁ × … × Mₙ` and public view `V`:

```text
assemble : M -> V
route    : V -> (M -> M)

read()   = assemble(currentBackingState)
write(v) = execute route(v) across the backing stores
```

`assemble` and `route` have the **operational form of a Lens** `Lens<M,V>`. If they satisfy the Lens laws, that is a useful, strong guarantee. Otherwise they form a `Selection<M,V>`, which is still composable. The type signatures alone do not prove laws.

## 2. Product: independently existing Storage<A> × Storage<B>

A first, small candidate API is the familiar Evoleq `x` operator (already used for `Source` products):

```kotlin
// Conceptual API; not implemented yet:
infix fun <A,B> Storage<A>.x(other: Storage<B>): Storage<Pair<A,B>>
```

Its value-level behavior is:

```text
(a x b).read()       = (a.read(), b.read())
(a x b).write(x, y)  = { a.write(x); b.write(y) }
```

An illustrative call:

```kotlin
val user    = Memory(User())
val context = Memory(Context())
val both: Storage<Pair<User, Context>> = user x context

// both does not replace or own user/context:
user.write(newUser)
check(both.read().first == newUser)
```

**Properties / restrictions:**

- Associativity is **up to the canonical isomorphism** `(A×B)×C ≅ A×(B×C)`; Kotlin's `Pair` nesting is not definitionally equal.
- Unit `Unit` is a unit for **value products**. It is not automatically an identity for arbitrary effectful `Storage<Unit>`: writing the unit may still cause side effects.
- Do not claim this is a categorical product in an unspecified category of effectful Storages; it is a **product-valued composite handle**.
- The operands are assumed *independently manageable*. They may alias the same underlying state even if they have different types. Two independently applied writes may interfere if their foci overlap.
- A write is performed on two backends, typically sequentially: **no atomicity, rollback, snapshot consistency, or failure recovery** is implied.
- Equality-based “write only if changed” optimization cannot be mandatory: a backend's write may have validation, logging or other effects. Reactive adapters may provide explicit, documented diff policies separately.

A corresponding `Product(a,b)` **capitalized standalone constructor** is a possible alternative to `x`; avoid publishing *both* until use-site ergonomics and overload conflicts have been tested.

## 3. General view on a product — existing storages are "absorbed"

For a product of independent backing states, there are two important use cases:

**Augmenting the type:** `Storage<A>` and `Storage<B>` yield `Storage<Pair<A,B>>`. This is ordinary product composition.

**Building a domain view:** existing `Storage<A₁>, …, Storage<Aₙ>` are assembled into `Storage<V>`, where `V` could be `Application`. This additionally needs **how to assemble** and **how to distribute writes**.

The mathematical information is:

```text
g : M -> V                 (assemble / get)
p : V -> (M -> M)          (route / put)
M = A₁ × … × Aₙ
```

A minimal *possible* usage (not a compiled API):

```kotlin
val backing = applicationMemory x modalMemory x contextMemory x processMemory
val view: Storage<Application> = backing * viewSelection
// viewSelection : Selection<BackingProduct, Application>
// Or a lawful Lens, if its laws are established.
```

This is very close to what Solyton does today, but the view and write distribution are now explicit and separately testable. It also reuses our existing `Storage * Selection/Lens` mechanism; a new “CompositeStorage” inheritance hierarchy may not be necessary.

A different but related use case is **mount/overlay**, in which an already complete `Storage<Application>` has an existing field **authoritatively replaced** by `Storage<Modals>` through a Lens `Lens<Application,Modals>`. That is not merely a product: the original Application backend may also contain a copy of Modals. We must define which copy is authoritative, how synchronization is handled, and whether writing a view triggers changes in the parent. The simplest *safe* architecture generally avoids duplicating authoritative values in the first place.

No `absorb`, `mount`, `overlay` or `attach` public name is chosen yet. “Absorbing” means **referencing existing backends**; it should not imply allocating a new Memory or transferring ownership of their lifecycle.

### Laws for assemble/route (a candidate lawful view)

For `m: M` and `v,v₁,v₂: V`:

```text
Get–Put:  route(assemble(m))(m) = m
Put–Get:  assemble(route(v)(m)) = v
Put–Put:  route(v₂)(route(v₁)(m)) = route(v₂)(m)
```

For instance, Solyton's current exclusive `when` router can fail Put–Get when `v` changes **more than one independently backed field**, because it applies only the first matching branch. It may still be a meaningful `Selection` and its exact runtime behavior must be tested, not guessed.

The equalities above concern **observable backing values**. They are not automatically equations between callback histories, UI invalidations or concurrent effect traces. We should document which equivalence relation is intended for each backend.

When two backends expose the **same logical field**, compatibility constraints enter: gluing them is no longer a free Cartesian product. One may need an equality constraint / pullback-like compatible subspace, a synchronization policy or one authoritative source. Do not silently treat overlapping stores as independent.

## 4. Sums: distinguish three different questions

Three superficially similar shapes should not be conflated:

```text
(1) Storage<A> + Storage<B>           // one of two storage handles
(2) Storage<A + B>                    // handle of a tagged sum value
(3) Storage<A> × Storage<B> + tag     // two backends plus active-branch selection
```

There is **no canonical general conversion** from (1) to (2) satisfying arbitrary writes: if the left Storage is the only one we possess, it cannot in general accept a value of the right type, and vice versa. This is not a Kotlin limitation but missing semantics.

To implement a switchable `Storage<Either<A,B>>` using both existing storages, we also need a **tag/active-branch policy** (possibly another Storage) and decisions about:

- Writes that switch sides: retain the inactive branch, clear it or construct a new value?
- Observability: which branch and which tag should invalidate a Compose consumer?
- Partial failures: when do we change the tag relative to the value, and how do we recover if either write fails?
- Optional absence: perhaps the right mathematical construction is a **Prism**, not a total Lens.
- Whether a tagged sum is the **value being stored**, or an alternative **command** sent to the product of stores (e.g. “update A or update B”). The latter is a natural way to route disjoint operations without requiring a switchable read value.

A sum of commands may be the practical first implementation. The generic `Storage<A+B>` algebra is deliberately **not implemented** until the operational semantics are agreed.

The sibling `toolkit-type-algebra` module already treats sum and product as different type expressions; that AST is **not a runtime implementation** of the Storage sum/product operators.

## 5. Compose: combine observation policies, not storage algebras

The existing frontend has independent backing behavior:

```text
Compose-observable:   application, modals
Plain/unobserved:     context, processes
Outer read/write:     Storage<Application>
```

The generic composite should neither know about Compose nor force all fields into one `mutableStateOf<Application>`. The same composite interface can reference an observable adapter and ordinary `Memory`. **A read during composition may subscribe to observable fields it accesses**; writes to plain memory do not themselves publish Compose snapshot invalidations. A later observable write can nevertheless cause rendering of a view that incorporates updated plain memory.

If a composite writes every field indiscriminately, it may produce avoidable notifications; if it writes **only the first different field**, it can silently drop simultaneous changes. A write policy that updates every relevant changed component must be designed with side effects and identity carefully in mind.

Lifecycle and cancellation of event subscriptions (`onInit`, `onChange`, `onDispatch`) are **not** functions of the algebraic read/write Storage; they belong to an effectful/Compose adapter layer.

## 6. What needs tests before we implement composition

1. **Product:** value-level Put–Get and unchanged fields for independent memories; product nesting associativity up to Iso; live reflection of updates to either original store.
2. **Aliasing/overlap:** two composed views onto one shared backing store; document whether interference is rejected, accepted or governed by a law.
3. **Domain view:** writing two changed fields in one call must update both; writing current view should not unexpectedly mutate state (Get–Put); successive writes must have clear semantics (Put–Put).
4. **Mixed observability:** plain memory does not automatically invalidate Compose, while reactive memory participates in snapshot observation; no stale values on the next read.
5. **Lifecycle:** composing views must not reinitialize existing memories, create duplicate event subscriptions or silently take ownership.
6. **Failure and concurrency:** at least one backend write fails; verify partial-write expectations, no false atomicity promise; test races where applicable.
7. **Sum:** explicit active-branch semantics, transitions and Prism-based partial focus before publishing a `+` operator.

## Dispatchers associated with composed storages

Two existing Storage handles admit a product-valued read/write composition, but two existing ActionDispatchers do **not** automatically yield a dispatcher for arbitrary actions on the product state. A natural candidate is a **tagged sum of commands**, routed explicitly to the corresponding backend. See [Action dispatch and Storage composition](ACTION-DISPATCH-AND-STORAGE.md) for the paired-capability Store design, Lens action lifting, envelope preservation and separate runner lifetime.

## Decision log / migration boundary

- **Confirmed:** keep `Storage<T>.read()` and `write(T)`. `source()` is a derived read projection. Keep existing handles alive and independently usable.
- **Confirmed:** ToolKit core stays Kotlin Multiplatform and Compose-free.
- **Candidate:** `Storage<A> x Storage<B> : Storage<Pair<A,B>>`, using existing Evoleq `x` vocabulary.
- **Candidate:** product backing followed by `Storage * Lens` or `Storage * Selection` for a domain-specific view; explicit algebraic laws.
- **Open:** whether the return type should use `Pair` or a later `Product2` type, how to support n-ary products ergonomically without huge tuples, and whether a general `Iso` can eliminate tedious wrapping.
- **Open:** tag and transition policy for `Storage<A+B>`, command sums, Prism, overlapping stores, atomic operations and backend effects.
- **Explicitly deferred:** copying/rewriting Solyton's frontend `@Composable Storage()`, generic `CompositeStorage` class, lifecycle integration, a `Storage` sum operator, and automatic Reactivity/Compose semantics.

**Reference:** [current storage design and frontend inspection](STORAGE-AND-MEMORY.md); [mathematical foundations](MATHEMATICAL-FOUNDATIONS.md).
