# Reconciling the legacy Storage PR with ToolKit's current core

**2026-11-18.** Resolves the original, conflicted [PR #3](https://github.com/evoleq/mathcat/pull/3) against the linear ToolKit master. The historical head was preserved on `archive/toolkit-pr3-before-reconcile-2026-11-18` before rewriting the PR branch.

## Kept

```text
Source<A> = Reader<Unit,A>                   (already in core)
Dispatcher<A> = Writer<Unit,A>               (synchronous command evaluator)
Storage<S> * Writer<S,A> -> Dispatcher<A>
Read(Storage<S>) -> Source<S>
Dispatch(Storage<S>) -> Dispatcher<S>
Source<A> x Source<B> -> Source<Pair<A,B>>
List<Source<A>>.emit() -> List<A>
```

A `Dispatcher<A>` is a Writer over Unit, not a coroutine dispatcher. The `dispatch(command)` extension evaluates the Writer once. The separate `ActionDispatcher<S>` in `toolkit-actions` is suspending and may only **accept** an Action; it does not imply execution. They are intentionally distinct.

The `Storage * Writer` interpretation samples the **current** `Storage.read()` at dispatch time and writes the resulting value using `Storage.write()`. Constructing the Dispatcher does not evaluate the Writer or read Storage. It is a synchronous read–modify–write, **not atomic**, and offers no concurrency, rollback or thread-safety guarantee.

`Source.x` constructs a deferred product. On each `emit()` it evaluates its two sources left-to-right. A pair of potentially effectful Sources is not an automatically commutative computation, even though the value-level product can be viewed up to isomorphism.

## Deliberately not kept

The legacy `evoleq.toolkit.storage.Storage` data class and `toolkit-storage` module duplicate and conflict with the already integrated, more general `evoleq.toolkit.core.storage.Storage` **interface**. The interface remains the only Storage contract; an implementation may assemble multiple independently owned Memories.

No Ktor, Compose or coroutine dependency is introduced, and no Solyton code changes. Existing Source, Lens, Selection, Reader and Storage APIs are retained. Tests cover deferred and live execution, `Writer.contraMap`, product evaluation order, failures and existing Storage identity.

This PR retains its original discussion and is merged via rebase from a new single compatible commit rather than replaying the 76 stale, mostly already integrated commits.
