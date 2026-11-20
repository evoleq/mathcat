# Compositional routing — implementation plan

**2026-11-20 · Plan, not an implementation.**

**Recommended artifacts:** `mathcat-routing` (pure MPP declarations and matching),
`mathcat-routing-browser` (JS browser adapter) and optionally
`mathcat-routing-compose` (Compose renderer).

**Architectural decision:** the **router is a subautomaton of the outer Application Flow**. Its state is not automatically Application domain data; the enclosing Flow may project an observable routing state into a Store-backed view.

## Existing Solyton router: preserve good ideas, fix weak contracts

The legacy [routing DSL](https://github.com/doctor-smith/solawi-bid/tree/main/evoleq/src/jsMain/kotlin/org/evoleq/compose/routing) already provides nested routes, named parameters, layouts/wrappers and conditional routes. The desired user-facing DSL should remain concise.

Source-level improvement targets:

- The route configuration merge groups certain variable branches by empty default values; conflicting variable siblings need an explicit ambiguity result.
- The parser's query-field handling uses semicolons; standard `&`-separated query parameters, percent encoding and repeated keys need a defined contract.
- Runtime navigation uses process-wide `currentPath`, mutates history in rendering, and assigns `window.onpopstate` without a scoped unsubscription; these should be owned by the browser interpreter.
- Route guards currently participate in route-building; evaluate permissions **on navigation** with current auth context and explicit denial/redirect results.

These are concrete source observations to verify in regression tests, not untested assertions about production incidents.

## Separate four concerns

```text
pure route declarations
       |
       v
CompileRoutes / Match : URL -> RouteMatch + RouteFailure  (pure)
       |
       v
Router automaton : Phase × NavigationEvent -> Phase × Commands (pure)
       |
       v
Application Flow : interprets commands, owns tasks and lifecycle
       |
       +---- browser history / auth / plugin loader / Compose rendering
       |
       +---- borrowed Store and optional observable route projection
```

Route matching is not a Prism itself. Use a Prism only to focus lawful alternatives of an explicit sum, such as `Matched + NotFound + Denied`. The router's **transition function** is the automaton; the dynamically loaded feature's activation remains an effect.

## Pure routing kernel

An illustrative DSL (API not finalized):

```kotlin
val router = routes {
    route("/app") {
        route("shares") {
            route("{shareId}") {
                name("shares.detail")
                requires("shares.read")
            }
        }
    }
}

val compiled = CompileRoutes(router)
val result = compiled.match("/app/shares/42?tab=history")
```

- Support `{param}` in named routes (and document any legacy `:param` compatibility).
- Normalize root/trailing slash, fragments, percent-encoded segments, query multimap, and internal URL constraints.
- Static segments outrank params; two competing param patterns or duplicate route IDs yield an explicit failure, never an arbitrary last-writer-wins.
- Route fragments from independent modules combine through a **validated** operation with an empty identity; associativity is expected only under well-defined disjointness/normalization preconditions.
- Guard metadata must not substitute for actual host-/server-side authorization.
- No Compose, DOM, Ktor, or plugin library dependency in pure `mathcat-routing`.

## Router as an internal subautomaton

Sample lifecycle:

```text
Idle --Navigate(url)--> Matching
Matching --Matched--> Guarding
Guarding --Denied--> Denied
Guarding --Allowed, plugin missing--> Loading
Loading --Activated(generation)--> Active
* --Stop--> Stopped
```

The **Application Flow** receives navigation events and routes them through the router reducer. It interprets explicit commands for browser history, checking current auth policy, and optional plugin activation via existing `Discover * Activate` and `AccessPlugin`. When a second navigation begins, superseded async completions must not overwrite the new location: request IDs/generations and cancellation are required.

Do not put independent `CoroutineScope(Job())` into the router. The outer Flow owns the event loop. For a standalone router application, a small router-owned runtime with an *externally supplied scope* can be used, but it must follow the same ownership contract.

Current route/location can be rendered through a projection of the Flow's observable state, potentially via `Store * Lens`. The **route registry**, browser `History` and renderer callbacks do not become fields of `Application` just for convenience.

## Browser and Compose adapters

- Browser port should implement push, replace, back/forward and popstate with `addEventListener/removeEventListener`; it must have deterministic disposal.
- Compose renderer selects a host-owned page renderer by route ID, with explicit Loading, Forbidden, NotFound and Error slots.
- Navigation effects must not be fired in a Composable body. Compose observes the Flow/Store projection and renders it; the Flow/Browser interpreter performs navigation.
- Distinguish legacy Compose HTML (Solyton) from Compose UI; compile/test adapters for concrete targets rather than assuming one renderer works everywhere.
- Plugin-backed routes should reference host-validated plugin IDs and a plain JS module ABI, never pass an independently compiled Kotlin `@Composable` function over the JS boundary.
- `mathcat-plugins-actions` currently implements fixed host routes and action grants, **not** dynamic route registration. Add glue only when required by a real end-to-end slice.

## Delivery sequence with regression tests

**R1:** Pure parser/trie, named parameters, query/encoding, conflicts and nesting; property/example tests independent of browser.  
**R2:** Route declaration composition, guards and explicit results; validate unit/associativity only within declared preconditions.  
**R3:** Pure router phase reducer and Application Flow integration; tests for denied/not-found/loading/retry/stop, cancellation and stale results.  
**R4:** JS browser history port + Compose HTML renderer; tests for back/forward, deep links, recomposition, multiple owner lifetimes and cleanup.  
**R5:** Reversible Solyton migration of one page, then optionally an independently deployed microfrontend, with authorization and load-once tests.

Do **not** roll all these tasks into one PR. Keep the boundary between mathematical routing, state transitions, effect interpretation and UI rendering visible and executable.
