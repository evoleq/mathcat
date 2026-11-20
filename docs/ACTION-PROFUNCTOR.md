# Action as a profunctor — ToolKit and the frontend

> Design and code reference for the compositional software architecture manuscript.
> Implementation: `toolkit-actions`. The `toolkit-middleware` interpreter is a
> **separate effect layer**, not part of the following pure construction.

## 1. The two directions in one action

Fix a state type `S` and consider pure functions as arrows in **Set**.

The Evoleq Action describes two independent interactions with the same state:

```kotlin
data class Action<S, I, O>(
    val name: String,
    val reader: Reader<S, I>,
    val writer: Writer<S, O>,
    val onSuccess: Writer<S, O>? = null
)
```

The reader extracts a command input `I`; the writer selects a state update
from a processed output `O`. Write `End(S) = Hom(S,S)`. Then:

$$
\begin{aligned}
\operatorname{Reader}(S,I) &\cong \operatorname{Hom}(S,I),\\
\operatorname{Writer}(S,O) &\cong \operatorname{Hom}(O,\operatorname{End}(S)),\\
\operatorname{Action}_S(I,O) &\cong
\operatorname{Hom}(S,I)\times
\operatorname{Hom}(O,\operatorname{End}(S)).
\end{aligned}
$$

Here `onSuccess` is an *optional second writer*, transformed on exactly
the same side as `writer`; it is not a second transport operation.

The directions are easy to confuse. Since `Reader<S,I>` is covariant in
`I` and `Writer<S,O>` is contravariant in `O`, the standard profunctor
argument order is **not** `(I,O)`, but `(O,I)`:

$$
P_S : \mathbf{Set}^{op}\times\mathbf{Set}\to\mathbf{Set},
\qquad
P_S(O,I) = \operatorname{Action}_S(I,O).
$$

The metadata `name` is unchanged by the following operations; the same
constructions can be made fiberwise over each fixed name.

## 2. The dimap operation

Given `before : P -> O` and `after : I -> J`:

$$
\operatorname{dimap}(before,after) :
\operatorname{Action}_S(I,O)
\to \operatorname{Action}_S(J,P),
$$

$$
(r,w) \longmapsto
\bigl(after\circ r,\; w\circ before\bigr).
$$

The same `w ∘ before` operation is applied to an optional `onSuccess`.
The Kotlin API is:

```kotlin
val next: Action<State, NewInput, NewOutput> =
    original.dimap(
        before = { newOutput: NewOutput -> oldOutput(newOutput) },
        after = { oldInput: OldInput -> newInput(oldInput) }
    )
```

The individual halves are `map(after)` and `contraMap(before)`.
All three operations describe **pure transformations**, not HTTP calls.

## 3. Laws

The identity law follows immediately:

$$
\operatorname{dimap}(\operatorname{id},\operatorname{id})(a)=a.
$$

For `f : O' -> O`, `f' : O'' -> O'`,
`g : I -> I'`, and `g' : I' -> I''`, the composition law is:

$$
\operatorname{dimap}(f',g')\bigl(
\operatorname{dimap}(f,g)(a)\bigr)
=
\operatorname{dimap}(f\circ f',g'\circ g)(a).
$$

Both sides produce identical Reader and Writer functions by associativity
of composition. In Kotlin, equality is tested *observationally* on outputs
and state updates, not by comparing function object identities.
See `ActionProfunctorTest`.

## 4. Transport is another interpretation

Do not confuse the profunctor's arguments with a transport endpoint
`EndPoint<I,O>`. Although Action's reader produces `I` for an HTTP
request and its writer accepts `O` from an HTTP response, a pure
`map` / `contraMap` does not generate a request/response serializer,
change the server protocol or update a declared endpoint key.

In ToolKit:

- `toolkit-actions` owns pure descriptors, their profunctor-style maps
  and lawful lens lifting over state.
- `toolkit-ktorx` interprets an endpoint's declared method, types and
  wire format, with manual or generated serializers.
- `toolkit-middleware` supplies the standalone `Call(action)` and
  the typed sequence `Read * Call * OnError * Dispatch * React`.
- Domain-specific permissions, UI error dialogs and process lifecycles
  stay in an application or a future optional process module.

After changing `I` or `O`, the application must supply a *compatible*
transport declaration or explicit boundary mappings. A profunctor law
is **not** a claim of wire compatibility.

This separation is particularly valuable in the book: the same pair of
categorical variances explains both the convenient `map` / `contraMap`
API and why application state updates can be composed without tying the
model to any particular HTTP or UI runtime.
