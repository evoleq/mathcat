# Domain, Boundary, Environment and multilevel dynamics

**Research slice · 2026-11-18 · not a frozen public API.** This is part of the architectural foundation of MathCat/Evoleq, rather than a decorative topology analogy. Companion to the book *Compositional Software Architecture*, chapters 2, 4 and 5.

## 1. Three roles, not three mutually exclusive classes

- **Domain** defines the state distinctions and rules meaningful for a local process, e.g. `D`, commands `U`, and outcomes `R`.
- **Environment** supplies relevant influences and dependencies (clock, configuration, repositories, HTTP/DB access, adjacent domains). An injected capability can be immutable as a handle while the resource it points to evolves. Separate *environment configuration/capabilities* from *environment state*.
- **Boundary** is the explicit interaction contract: what may enter or leave, what is observable, what can be injected, and how local behavior is embedded in or translated to a larger context. Boundary is not identical to the domain's data model, a Ktor route or a network API.

All three are **roles**: the same subsystem may be a domain at one level and an environment for another. A boundary need not be an HTTP boundary; a test boundary can supply functions/ports without real infrastructure. This is the author's earlier motivation for using a Boundary as a form of dependency injection in tests. The exact old Boundary code must be located and law-reviewed before reusing its historical API.

## 2. Discrete evolution with explicit coupling

A useful local teaching signature is

```text
step_D : D × U × E -> R × D
```

where `E` may be an explicit slice or observation of the environment rather than a global object. A production interpreter decides how to obtain that slice and realizes any effects. A pure state step alone does not perform IO or prove atomicity.

Consider a whole state `W`, a local domain `D`, and a lawful `Lens<W,D>` with `get:W→D` and `set:D→W→W`. A local update `f:D→D` lifts to

```text
lift_L(f)(w) = set(f(get(w)))(w).
```

This is a *focused* evolution. A local behavior depending on external influence `u` can be lifted only once the coupling has been specified:

```text
beta : W × U_W -> U_D
F_W(w,u) = set(F_D(get(w), beta(w,u)))(w).
```

Then the following **observational consistency** equation is meaningful:

```text
get(F_W(w,u)) = F_D(get(w), beta(w,u))
```

provided the Lens Put–Get law holds. No claim is made that a generally coupled subsystem admits an autonomous transition `D→D`: two whole states with identical focus can evolve differently because of their environments.

For autonomous evolution `phi_t:D→D`, `phi_0=id` and `phi_(t+s)=phi_t ◦ phi_s` are flow/semigroup laws only when time and evolution satisfy the relevant assumptions. For driven processes, use a transition family or cocycle depending on the driving input/history; commands, retries, clocks, cancellation and non-determinism do not imply a global continuous flow.

## 3. Several distinct levels of dynamics

1. **Value/state level:** a pure transition changes local domain data and preserves its invariants.
2. **Workflow level:** ordered Actions/Kleisli steps consume outcomes; outcomes may branch and failures need explicit policies.
3. **Subsystem level:** local domains interact via boundaries; environments drive coupled evolutions. Source/Storage/Store may connect current state to execution but do not create atomicity.
4. **System level:** a larger process coordinates subdomains, scheduling, persistence, backpressure and security. A composition of local lawful Lenses is not automatically a lawful composition of distributed effects.
5. **Architecture-change level:** the component graph, boundaries and interpreters may evolve through refactoring/replacement. This is a *different* dynamic on descriptions/implementations, not automatically the runtime flow of a domain.

Specify the time index and observation at each level. If a quotient/projection `p:W→D` is expected to commute with evolution, test that equation with its coupling assumptions; otherwise treat it as a desired property or a counterexample, not a theorem.

## 4. Topological language: precise analogies and limits

- **Boundary:** the interface across which influence and observations pass. It is *not* automatically the topological boundary `∂X` of a defined topological space.
- **Embedding/locality:** a Lens lifts transformations into a larger value context. This does *not* establish a topological embedding; an injective continuous map and a topology would be needed for that claim.
- **CW-complex:** attaching a module along an interface is a useful diagrammatic analogy to attaching a cell along an attaching map. A software module graph is not automatically a CW complex.
- **Surgery/refactoring:** replacing a subsystem while preserving an agreed boundary behavior is analogous to surgery. The preserved observable contract must be stated; no differential-topological surgery theorem is imported.
- **Homotopy/continuous change:** a family of implementations retaining observables is a useful metaphor for gradual migration, but an actual homotopy requires a topology and continuous deformation.
- **Ricci flow:** architectural simplification over a sequence of refactorings is an intuition, **not** Ricci flow or a derived curvature-based optimizer absent a mathematically defined geometric structure.

These analogies should guide diagrams and hypotheses. Do not disguise them as established laws of program architecture.

## 5. Evidence from predecessors

### Evoleq flow and gap (2018–2020)

- [`evoleq/evoleq-core` Flow](https://github.com/evoleq/evoleq-core/blob/master/src/commonMain/kotlin/org/drx/evoleq/evolution/flow.kt) combines an `EvolutionConditions<D,T>` predicate with an iterative `KlEvolving<D,D>` morphism.
- [`doctor-smith/evoleq` Gap](https://github.com/doctor-smith/evoleq/blob/master/src/main/kotlin/org/drx/evoleq/gap/gap.kt) has `from`, `to`, `deepen`, `widen`, `fill`: a prior attempt at lifting a focused evolution into a whole.
- The older [Gap tests](https://github.com/doctor-smith/evoleq/blob/master/src/test/kotlin/org/drx/evoleq/gap/gap-test.kt) demonstrate nested updates, and also contain an explicit failing/unimplemented `gapCancellation` test. The legacy `Flow` implementation owns coroutine scope and has blocking/GlobalScope patterns. **These are design precedents, not code ready to port unchanged.**

Re-evaluate `Gap` against current `Lens` laws and state/suspend boundaries. An arbitrary effectful Gap does not necessarily obey Lens laws or compose with consistent concurrency/cancellation.

### Existing Solyton/Evoleq API DSL (read-only)

[`evoleq/.../ktorx/api/api.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonMain/kotlin/org/evoleq/ktorx/api/api.kt) defines:

- `Api { post<S,T>(key,url); get<S,T>(key,url) }` and typed `EndPoint.Get/Post/Put/Patch/Delete/Head` variants;
- `group(name)` and `module(path, subApi)` as hierarchical endpoint configuration;
- a mutable `HashMap<KClass<*>, EndPoint<*,*>>` registry, with `EndPoint(key)` performing an unchecked cast.

The existing [ApiTest](https://github.com/doctor-smith/solawi-bid/blob/main/evoleq/src/commonTest/kotlin/org/evoleq/ktorx/api/ApiTest.kt) covers basic configuration but does **not** verify duplicate-key policy, nested `module` composition, path normalization, codec interoperability or the safety of the typed lookup. Current `module` overwrites keys and concatenates strings for paths. These are **potential** defects or ambiguity, not reproduced failures.

A better **future** boundary DSL should preserve fluent typed declaration while making typed keys, ownership, duplicate conflicts, prefix normalization, request/response codecs, error contracts, transport-specific interpretation and module composition explicit. Do not move Ktor serializers or endpoint maps into `mathcat-core`.

In the existing backend, [`Environment`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-backend/src/main/kotlin/org/solyton/solawi/bid/application/environment/environment.kt) combines `KTorEnv`, `DbEnv`, `JwtEnv`, configuration and external resources. This is a concrete adapter/environment assembly, not the abstract domain model. Dependency injection through a Boundary should let tests replace functions without constructing a real HTTP server or database.

## 6. Proposed seam, with no API commitment

```text
Domain dynamics (pure transition/Action description)
          ↑ explicit input/output contracts
Boundary (ports, typed mapping, observation and function injection)
          ↔ Environment capabilities + state
          ↓ effectful interpreters
Ktor / database / timer / queue / test fake
```

In the short term retain `Reader`, `Writer`, `Lens`, `Storage`, `Store` and `Action`. Experiment with Boundary/Environment and a purely modeled step in a **separate small module or research slice**, rather than prematurely adding transport dependencies to the algebra core.

### Tests to require before adopting a public API

1. The local update and its lifted whole update satisfy the projection equation for a lawful Lens.
2. A coupled counterexample shows that local focus alone does **not** determine next state.
3. Sequential local steps compose without rewriting the enclosing system (for pure, deterministic state).
4. Boundary injection replaces real operations with test functions and preserves the same domain contract.
5. API DSL: duplicate keys are rejected or resolved by an explicit policy; nested module paths compose associatively under declared normalization.
6. Boundary decoding and encoding have a precise roundtrip/observation law, including failure and version skew.
7. Multiple levels of timing (domain tick vs workflow completion vs architectural change) are not silently conflated.

No Solyton changes, no stable API claim, no automatic MathCat release from this documentation/testing slice.
