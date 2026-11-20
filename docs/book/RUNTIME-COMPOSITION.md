# Runtime Composition — When Applications Become Composable

> **Manuscript draft** for *Compositional Software Architecture*. Proposed placement:
> after the chapters on Actions, Boundaries and effectful composition, before
> the discussion of architectural evolution. This is book prose, not a claim
> that the chapter has already been inserted into the separate full manuscript.
> Code examples refer to MathCat `master` as of 20 November 2026.

A software architecture usually describes how components are assembled before
an application starts. We define modules, compile them, link their
dependencies, and deploy the resulting system. Yet it is tempting to ask a
different question: **Could the same principles of composition remain valid
when the set of components changes while the application is running?**

This is more than late loading for performance. A dynamically loaded plugin
may have been built and deployed independently of its host. The host must
determine what it is, whether its dependencies are compatible, which
operations it is allowed to perform, how it is activated, and what should
happen when it is no longer needed.

The important insight is that these questions do not require one enormous
plugin framework. They can be separated into small structures with different
mathematical roles.

## 1. From static modules to runtime composition

Consider an application providing navigation, identity and a common user
interface. A banking or shares feature may not be needed immediately, or may
have been released separately. The host initially knows only a *description*
of the feature. When a user requests it, the host must bring the executable
component into its running environment.

There are four distinct stages:

```text
       Describe                 Resolve
  PluginManifest  ───────────▶  PluginPlan
                                      │
                                      ▼
                                   Load
                                      │
                                      ▼
                                  Activate
                                      │
                                      ▼
                               PluginDeployment
                                      │
                                 Dispose / Stop
```

The manifest is immutable data: identity, version, artifact location,
dependencies and required capabilities. Resolution examines this data and
produces a dependency-first activation plan. Loading and activation are
effects: they interact with files, browsers, class loaders or other runtime
services.

We do not gain clarity by pretending these stages are all pure. We gain it
by making their boundary explicit.

## 2. Dependency planning as a pure operation

Write \(M\) for a manifest, \(Q\) for the requested plugin identifiers,
\(T\) for a target platform, and \(H\) for the host's declared protocol
and capabilities. A useful signature for the planner is

$$
\operatorname{Plan}:
M\times Q\times T\times H
\longrightarrow E+P,
$$

where \(E\) is a set of explicit planning failures and \(P\) contains an
ordered dependency plan.

This is a sum type: either the planner returns an error or it returns
a valid plan. Missing dependencies, version mismatches, unavailable
capabilities and dependency cycles are *values*, not hidden exceptions.

The graph of dependencies is not generally a tree. Two components may depend
on the same third component; a valid deployment order is therefore a
topological ordering of a directed acyclic graph. Planning must detect cycles
rather than attempting to load indefinitely.

In MathCat the planner is a pure function. That matters because it can be
tested without a browser, network, database or JVM class loader. The same
function applies on all supported Kotlin Multiplatform targets.

## 3. The Kleisli structure of loading

A plugin stage has the shape

$$
K(A,B)=A\longrightarrow \operatorname{suspend}(E+B).
$$

The error sum makes failure propagation explicit; suspension permits the
interpreter to cross an asynchronous boundary. For stages
\(f\in K(A,B)\) and \(g\in K(B,C)\), define the left-to-right operation
\(f*g\) by

$$
(f*g)(a)=
\begin{cases}
\operatorname{Left}(e), & f(a)=\operatorname{Left}(e),\\
g(b), & f(a)=\operatorname{Right}(b).
\end{cases}
$$

Here \(g\) runs only when \(f\) succeeds. This is Kleisli composition for a
checked, potentially suspending computation. Its type tells us which stages
fit together and its laws tell us how a composed sequence behaves, subject to
the observable effects of executing it.

MathCat deliberately keeps the operator visible:

```kotlin
val deployment =
    Discover(catalog, target, host) *
    Activate(loader, target, host)

val result = deployment.run(setOf(PluginId("shares")))
```

The mathematical structure is not a decorative comment next to the code.
It is the shape of the code.

However, the algebra does **not** turn activation into an atomic transaction.
If activation of a later plugin fails, the runtime can attempt compensating
cleanup in reverse order. It cannot conclude from associativity that every
side effect has been undone. The implementation calls this *best-effort
cleanup*, and its tests verify that limited promise.

## 4. A Prism for something that may not be present

The next requirement arrives naturally: *Do not load the same plugin
again when it is already active.*

We can represent the local runtime state as a tagged sum:

$$
\operatorname{PluginSlot}
\;=\;
\operatorname{Missing}
\;+\;
\operatorname{Loaded}.
$$

A Prism focuses a particular alternative of such a sum. Following MathCat's
convention, its two pure components are

$$
\begin{aligned}
\operatorname{match}&:S\to A+S,\\
\operatorname{build}&:A\to S.
\end{aligned}
$$

A successful match is placed in \(\operatorname{Left}\). The relevant laws
include

$$
\operatorname{match}(\operatorname{build}(a))
  =\operatorname{Left}(a)
$$

and, when \(\operatorname{match}(s)=\operatorname{Left}(a)\),

$$
\operatorname{build}(a)=s.
$$

MathCat's `LoadedPlugin` Prism focuses the loaded case of
`PluginSlot`. It neither fetches a manifest nor imports JavaScript.
That separation matters: **`build` constructs a value; it does not load
executable code.**

Instead, an effectful step interprets a failed match:

```kotlin
val access =
    MatchPlugin() *
    LoadIfMissing(
        selection = setOf(PluginId("shares")),
        catalog = catalog,
        loader = loader,
        target = PluginTarget.JS,
        host = host
    )

val result = access.run(currentSlot)
```

On a hit, the existing deployment is returned without calling the loader.
On a miss, `Discover * Activate` runs and the resulting value is reinserted
using the pure `Prism.build` operation.

This is an instructive division of responsibilities: **the Prism expresses
the distinction, the Kleisli arrow expresses the effect, and the host owns
the resulting lifecycle**.

There is a practical qualification: the current `PluginSlot` represents one
selected set of plugins. It is not a concurrent, shared, multi-plugin registry.
A different selection produces an explicit mismatch rather than silently
replacing a running component. We should resist calling it a general registry
before such a registry has actually been designed.

## 5. One algebra, different interpreters

Kotlin Multiplatform makes a useful distinction unavoidable. The common
description and planner can be shared; executable loading cannot be
implemented in the same manner on every platform.

On the **JVM**, MathCat loads a local JAR using a class loader and the
`ServiceLoader` contract. It checks the declared SHA-256 digest before
loading. Closing the class loader is part of resource cleanup; it does not
promise that the JVM immediately unloads every class.

In **JavaScript**, a plugin can be an independent ES module, loaded through
`import()`. The browser host revalidates a manifest that points to
content-hashed, immutable URLs. New module releases need not be packaged into
the original host build. The module's `dispose` operation removes
contributions such as UI elements and subscriptions, but it does not delete
the ES module from the browser's module registry.

The shared algebra therefore has more than one interpreter, and not every
platform has an executable loader. A target may support planning without
supporting dynamic native-code loading. That is a *useful distinction*, not a
failure of the multiplatform model.

The cache policy follows the same separation. A revalidated manifest answers
*which version should we use?*; an immutable content-hashed file answers
*which bytes belong to that version?* Neither content hashing nor the
manifest alone provides code authenticity, process isolation or binary
compatibility.

## 6. Plugins should receive a boundary, not an application

A late-loaded module must communicate with its host. Giving it the complete
application state and dispatcher is the easiest integration mechanism—and
often the least desirable one. It dissolves the boundary the module was meant
to preserve.

MathCat therefore provides an optional, separate bridge to its typed Action
architecture. The host binds identifiers to **host-owned**
`Action<S,I,O>` values or Action envelopes and to fixed internal routes.
A plugin uses the plain JavaScript interface:

```javascript
export const id = "shares";
export const version = "1.0.0";

export async function activate(host) {
    await host.actions.dispatch("shares.refresh");
    await host.navigation.navigate("shares.home");
    return { dispose() {} };
}
```

This example assumes that the host has granted both capabilities and bound
the two identifiers. The JavaScript plugin does not receive a Kotlin
`Storage<Application>`, an `ActionDispatcher<Application>`, or arbitrary
router access. The host checks its per-plugin allowlist for every command;
a retained JavaScript callback is revoked after plugin disposal.

This is a **capability boundary**, not proof of security isolation. JavaScript
imported into the same browser execution context is not sandboxed by such an
API. Server-side authorization is still required. Independently trusted or
untrusted plugins may need stronger process, origin or iframe separation.

The optional bridge also illustrates a point made earlier in this book:
a Boundary need not be an HTTP API. It can be a set of permitted functions
offered to a component while the environment retains their implementation.

## 7. Runtime composition as architectural evolution

We have followed the same pattern at several scales:

- Functions compose into computations.
- Readers, Writers and Lenses compose local interactions with larger state.
- Actions compose domain descriptions with their interpreters.
- Kleisli arrows compose effectful workflows.
- **Dynamically loaded components compose applications at runtime.**

These are not all the same operation, and not every operation inherits the
laws of the preceding one. Yet the design vocabulary is remarkably stable:
identify the structures, make their compositions explicit, state their laws,
and isolate effects behind boundaries.

This suggests a specific form of architectural evolution. A host can acquire
new behavior while retaining its existing contracts. The component graph
changes, but the method by which components are validated, loaded and
integrated need not be reinvented.

An architectural change is not automatically a homotopy or a topological
surgery. Such analogies become mathematical only once the underlying spaces,
morphisms, observations and equivalences are defined. They remain valuable
guides for visualizing a subsystem replacement along a preserved boundary.

The more immediate practical statement is strong enough:

> **A compositional architecture can make not only the components of a
> system, but the process of extending the system, more compositional.**

This is not a claim that every new requirement will be easy. Compatibility,
security, lifecycle, concurrency and deployment remain difficult.
The achievement is that each difficulty can be assigned an explicit place
and a testable contract instead of becoming another special case scattered
through the host.

## 8. What the example actually establishes

The implementation and its documentation tests establish a bounded result:

- A pure, deterministic dependency planner rejects cycles and incompatible
  dependencies.
- A Prism can select an already-loaded local plugin slot according to its
  documented match/build laws.
- The effectful pipeline loads on a miss and reuses the deployment on a hit.
- JVM JAR and JS ES-module interpreters exercise actual runtime loading.
- An optional host bridge gives plugins restricted command and navigation
  access without exposing the application's typed state.
- Disposal attempts ordered cleanup and revokes exposed JS command callbacks.

The example does **not yet** establish concurrent registry semantics,
transactional hot-swapping, signed browser plugin verification, untrusted
plugin isolation, complete Solyton navigation integration or an automatic
upgrade protocol. Those are legitimate future extensions, not hidden
premises of the current design.

### Notes for manuscript integration

- **Suggested earlier chapters:** lawful Lens/Prism, sum/product types,
  Action as profunctor, and Kleisli/effectful composition.
- **Suggested next chapter:** architecture change, compatibility invariants,
  boundaries and observable behavior during subsystem replacement.
- **Running example:** a `shares` feature requested by navigation and
  dynamically loaded into a Solyton-like host. This is based on MathCat's
  tested prototype; **the current production Solyton application has not
  been migrated to this plugin runtime**.
- **Figures to draw:** (1) manifest-to-plan-to-runtime interpreter,
  (2) `Missing + Loaded` Prism plus effectful miss branch,
  (3) boundary between plain JS callback keys and typed host Actions.
- **Code/evidence references:** `docs/PLUGIN-COMPOSITION.md`,
  `mathcat-core/.../Prism.kt`,
  `mathcat-plugins/.../PluginAccess.kt`,
  `mathcat-plugins-actions/.../ActionNavigationBridge.kt`, and
  `mathcat-plugins` common/JVM/JS tests.
