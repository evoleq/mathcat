# Pure Router — executable lifecycle specification

**2026-11-20. Experimental pure Kotlin Multiplatform slice.**
Stacked on MathCat PR #26. This is not a browser adapter, a Compose
renderer, a Ktor endpoint, or an application runtime.

## The Evoleq style: describe, compose, interpret

The Solyton-inspired nested routing vocabulary remains compact:

```kotlin
val catalog = routes {
    route("app") {
        layout("shell")
        requires("authenticated")
        route("organisationen/:organisation/anteile") {
            name("shares.detail")
            requires("shares.read")
            plugin("shares")
        }
    }
}
val checked = CompileRoutes(catalog) // Sum2<List<RouteError>, CompiledRoutes>
// After handling Sum2.Left errors:
val router = Router(compiled)
val next = router.evolve(
    RouterState(),
    RouterEvent.Navigate("/app/organisationen/sonnenhof/anteile")
)
```

In this example, `compiled` is the validated `CompiledRoutes` value from
the successful `Sum2.Right` result. The tests use `assertIs` for this
check, rather than pretending that compilation cannot fail.

The router is an ordinary `Flow<RouterState, RouterEvent, RouterCommand>`.
Its mathematical signature is

\[
\delta: RouterState \times RouterEvent
    \longrightarrow RouterState \times RouterCommand^*.
\]

Every command is **data**. No network call, browser history mutation,
plugin import or coroutine starts in a transition. The host interprets
commands and sends completion events through its own event loop.

## Lifecycle

```text
Navigate(url) ──► Match ───── not found / invalid ────► Rejected
                   │
                   ▼
              needs access?
               /       \
             yes        no
              │          │
              ▼          │
           Checking      │
           /      \      │
        denied  granted  │
           │       │     │
           ▼       ▼     ▼
         Denied   Plugin needed?
                       /    \
                      no    yes
                      │      │
                      ▼      ▼
                    Active  Loading
                      ▲     /     \
                      │  ready    failed
                      │   │         │
                      └───┘         ▼
                                 Failed

* ── Stop ───────────────────────────► Stopped
```

For a guarded route, `CheckAccess(generation, match)` is emitted before
`LoadPlugin(generation, pluginId)`. A declaration `requires("shares.read")`
is **not** an authorization decision. The host must check permissions in
its live identity/context boundary; the server must authorize independently.

`plugin("shares")` is only route metadata. After a positive decision,
the host can interpret `LoadPlugin` using existing MathCat Prism-based
loading. An already loaded plugin may yield an immediate
`PluginActivated`; the reducer itself never decides when a loaded
plugin may be reused.

## Generations and cancellation requests

Every accepted navigation receives a new generation. Results for a
different generation, wrong phase or wrong plugin ID are ignored.
The generation prevents a slow completion for an old URL from replacing
the current route.

A new navigation superseding `Checking` or `Loading` emits
`CancelPending(oldGeneration)` **before** new commands. Cancellation is
a request to the owning interpreter; it is not evidence that a network
call has been rolled back or a plugin has been disposed.

`Stop` is idempotent. It prevents further navigation transitions, and
requests cancellation for in-flight work. It does **not** dispose a
shared or borrowed plugin, a Storage, or the application CoroutineScope.

## Address-bar and browser-history contract

For a deep link already present in the browser, use:

```kotlin
RouterEvent.Navigate(
    "/app/organisationen/sonnenhof/anteile",
    HistoryMode.None
)
```

This prevents duplicate history entries on initial load or
`popstate`. User-driven navigation uses `HistoryMode.Push`;
redirects can use `HistoryMode.Replace`. `ChangeHistory` is emitted
only when a route becomes `Active`, not when access fails.

## Test cases (as executable documentation)

| Test | Contract |
| --- | --- |
| `documentedNavigationSequenceChecksAccessThenLoadsPluginThenCommitsHistory` | Guard, load and history order |
| `browserDeepLinkNeverPushesItselfAgain` | Direct URLs and encoded slugs |
| `replaceNavigationEmitsReplaceOnlyAfterActivation` | Replace semantics |
| `deniedRouteNeverRequestsPluginOrChangesHistory` | Guard before loading |
| `staleAuthorizationCannotActivateTheNewLocation` | Generation checks for permission results |
| `stalePluginResultAndWrongPluginIdCannotOverwriteNewRoute` | No stale plugin activation |
| `pluginFailureIsExplicitAndRetryGetsFreshGeneration` | Failure and fresh retry |
| `navigationToMissingRouteRejectsAndCancelsPendingGeneration` | NotFound and cancellation |
| `invalidEncodedDeepLinkIsAnExplicitRouteFailure` | Invalid URL diagnostics |
| `stopCancelsInFlightWorkAndIsIdempotent` | Stop and no post-stop effects |
| `stopDoesNotDisposeBorrowedOrActivePlugins` | Ownership |
| `eventReplayIsDeterministicAndNoEffectIsExecuted` | Reducer determinism |
| `guardMetadataIsNotMisrepresentedAsAuthorization` | Metadata is not permission |

The test suite runs against the shared KMP commonTest sources on the
project's configured CI targets. This module declares an ordinary
Gradle/compatibility dependency on `toolkit-evolution`.

## Outside the current slice

The browser History port, the Compose HTML renderer and the owner-scoped
Application Flow runner remain separate implementations. A later
integration fixture must establish real cancellation, listener cleanup,
browser reload/back-forward, plugin load-once, permission rechecks,
recomposition and disposal.

Browser preferences and IndexedDB remain separately planned effect
boundaries (see `docs/plans/BROWSER-PERSISTENCE.md` and
`docs/plans/INDEXEDDB-OFFLINE.md`). They are not disguised as pure Lenses
or hidden in the Store constructor.
