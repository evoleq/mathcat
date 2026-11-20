# Flow and Routing: first executable, pure DSL slice

**2026-11-20. Experimental APIs; implementation PR, not a production router/runtime.**

These two optional Kotlin Multiplatform modules follow the architectural direction
in [EVOLUTION-FLOW](plans/EVOLUTION-FLOW.md) and [ROUTING](plans/ROUTING.md).
No dependency on Ktor, Compose, browser DOM or a coroutine runner is added.

## A Flow is a transition, not a CoroutineScope

A Evoleq ToolKit Flow is the pure function

\[
\delta: S \times E \longrightarrow S \times C^*
\]

where \(C^*\) is an ordered list of descriptions of effects. The application
interprets commands **outside** the reducer, with an owner-controlled lifecycle.

```kotlin
sealed interface Event {
    data object Start : Event
    data class Navigate(val url: String) : Event
}
sealed interface Command {
    data object ReadEnvironment : Command
    data class MatchRoute(val url: String) : Command
}
data class AppState(val location: String = "/")

val navigation = Flow<AppState, Event, Command> {
    on<Event.Navigate> { state, event ->
        Evolve(
            state.copy(location = event.url),
            Command.MatchRoute(event.url)
        )
    }
}

val application = Flow<AppState, Event, Command> {
    on<Event.Start> { state, _ ->
        Evolve(state, Command.ReadEnvironment)
    }
    include(navigation)
}

val next = application.evolve(AppState(), Event.Navigate("/app/organisationen/sonnenhof"))
// The command is data. Nothing was launched or sent to a browser.
```

`Flow` is a capitalized constructor; `Evolve` yields a value. Typed `on<Event>`
dispatch, predicate-based dispatch and `include` work without dynamic
registration. Rules are tried in declaration order; the first matching rule
wins. `otherwise` is an explicit fallback. The implicit unmatched behavior
is identity with no commands.

Two pure `Transition<S,E,C>` values also compose with `*`: the second
receives the state returned by the first, and their command lists are
concatenated in order. `IdTransition()` is the identity; tests document
both identity and associativity. This composition **does not execute**
commands and is not a replacement for the later effectful interpreter.

These semantics are intentionally **not** a claim that the Flow is an
autonomous Evoleq `Flow<D,T>` loop, a coroutine `Flow` or a structured
application runtime. The historic evolution equations and conditions
will inform the external runner design. We should establish two real
reducer/runner use cases before adding abstraction.

## Preserve Solyton's nested routing vocabulary

```kotlin
val shares = routes {
    route("organisationen/{organisation}/anteile") {
        name("shares.detail")
        requires("shares.read")
    }
}
val app = routes {
    route("app") {
        layout("shell")
        requires("authenticated")
        include(shares)
        route("auctions/:auctionId") { name("auction.detail") }
    }
}
val compiled = CompileRoutes(app)
```

`CompileRoutes` returns `Sum2<List<RouteError>, CompiledRoutes>`:
duplicates and competing variable branches are **values**, not arbitrary
last-writer-wins behavior. `CompiledRoutes.match(url)` and
`CompiledRoutes.url(name, params, query)` use checked `Sum2` results.
Static segments win over variables. Named parameters work both as `{id}`
and Solyton's `:id`; query keys may repeat, and UTF-8 path/query
components use percent encoding (no global mutable path).

A host maps the match's `layouts` and `requires` metadata into real
renderers and authorization decisions. **A route permission string is not
an authorization check**. The original Solyton `component`, `wrap`,
`access` and browser-history hooks need dedicated adapters rather
than moving Compose or DOM into the pure routing kernel.

## Boundaries and next PRs

1. Integrate the router reducer into an external application-owned event
   loop. Define bounded delivery, stop, cancellation, stale result IDs and
   effect interpreter policies before describing anything as an app lifecycle.
2. Add a JS History adapter with `addEventListener`/`removeEventListener`;
   verify deep linking, back/forward and ownership/disposal.
3. Add an optional Compose HTML renderer and a reversible Solyton fixture.
4. Implement browser preference persistence as independent checked
   boundaries; IndexedDB transactions and auth storage migration are
   separate later slices, as documented in the plans.

Tests cover typed Flow dispatch, ordered commands and stable routing
roundtrips, precedence, ambiguity, encoding and module composition.
