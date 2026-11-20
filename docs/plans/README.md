# MathCat Compose and routing — development plans

**2026-11-20 · Plans only; no new API implemented by these documents.**

**Repository recommendation:** keep everything in `evoleq/mathcat` as **independently published optional KMP modules**. A separate `evoleq.composex` repository is not needed merely to keep Compose dependencies out of the core; source-set/module boundaries do that already. Reconsider only if an adapter later needs a genuinely independent release cadence or maintainers.

Five complementary plans:

1. [Evolution Flow: automaton around the Store](EVOLUTION-FLOW.md) — preserve the author's mathematical Flow but replace detached and blocking coroutine constructs with a scoped runtime.
2. [Stable @Composable Store](COMPOSE-STORE.md) — small Compose observation/receiver adapter; **no automaton inside Store**.
3. [Compositional Routing](ROUTING.md) — pure route algebra plus **router subautomaton inside Application Flow**, with browser and Compose interpreters.
4. [Functional Browser Persistence](BROWSER-PERSISTENCE.md) — typed key/value and cookie boundaries; effect interpretation by the outer Flow, not the Store.
5. [IndexedDB & Offline](INDEXEDDB-OFFLINE.md) — asynchronous transactions, schema migration and cautious optional synchronization.

```text
            Application Flow: owns events, state evolution, lifetimes
                   |
          Router subautomaton
                   |
     uses  Store<S,D> + effect interpreter ports
                   |
       mathcat-compose + browser renderer
```

**Principle:** mathematical transitions are visible and testable. State and resource ownership may live in a closure, but the closure never hides an unstructured coroutine. Write concrete transition tests **before** extracting a new `mathcat-evolution` public library.

For browser persistence, begin with a typed in-memory port and actual Solyton preference/context use cases; treat its current localStorage token handling as a separate security-review item. Do **not** introduce IndexedDB or a new generic storage framework before there is a real offline use case.

**First implementation step recommended:** a pure routing kernel and a two-machine proof (routing vs Store processor) before committing to a shared runner abstraction. Then an optional Compose adapter with one Solyton integration fixture.

These plans deliberately do not change the running Solyton frontend or declare its existing implementation defective without a demonstrated regression.
