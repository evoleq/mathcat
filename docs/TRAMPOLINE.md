# Trampoline: a small, stack-safe monad

**MathCat Core · commonMain · Kotlin Multiplatform**

The mathematical vocabulary deliberately matches the existing MathCat
`State` and `Result` implementations:

| Mathematical operation | Kotlin API | Type |
| --- | --- | --- |
| Monad unit / `η` | `Return(a)` | `A -> Trampoline<A>` |
| Functorial map | `t.map(f)` | `T<A> -> T<B>` |
| Monad multiplication / `μ` | `nested.multiply()` | `T<T<A>> -> T<A>` |
| Derived Kleisli bind | `t bind f` | `T<A> -> (A -> T<B>) -> T<B>` |
| Composition shorthand | `t * f` | same as `bind` |
| Delayed step | `More { ... }` | `(() -> T<A>) -> T<A>` |
| Interpreter | `t.run()` | `T<A> -> A` |

## Mathematical structure

A trampoline has a terminal result or a deferred step:

```text
T(A) = A + (() -> T(A))
```

`Done` and `More` expose these two constructors; the actual implementation
also uses an **internal** `Binding` constructor to represent deferred function
composition. That third constructor is an implementation detail required for
stack-safe *nested* `map`, `multiply` and `bind` operations.

The central equation is visible in the Kotlin implementation:

```kotlin
infix fun <A, B> Trampoline<A>.bind(
    f: (A) -> Trampoline<B>
): Trampoline<B> = map(f).multiply()
```

No `flatMap` alias is needed: `multiply` is the primitive and `bind` is
derived. This follows the same Evoleq naming as the existing State monad.
`Return` is a **standalone capitalized constructor**, not a companion
method. Import it from `org.evoleq.mathcat.core.trampoline`.

## Example: recursion without JVM-stack recursion

```kotlin
import org.evoleq.mathcat.core.trampoline.*

fun countdown(n: Int, sum: Int = 0): Trampoline<Int> =
    if (n == 0) Return(sum)
    else More { countdown(n - 1, sum + 1) }

check(countdown(50_000).run() == 50_000)
```

Calling `countdown(50_000)` creates **one** deferred step; the `run()`
interpreter advances using an iterative loop.

## Example: composing without running

```kotlin
val f: (Int) -> Trampoline<Int> = { n ->
    More { Return(n + 1) }
}
val program = Return(4) * f * f

check(program.run() == 6)

// The same program without composition shorthand:
check((Return(4) bind f bind f).run() == 6)
```

Neither `More` nor `map`/`multiply`/`bind` executes its thunk while the
program is being composed.

## Monad laws, verified observationally

For pure functions `f : A -> T<B>`, `g : B -> T<C>` and `m : T<A>`:

```text
Return(a) bind f             == f(a)
m bind Return               == m
(m bind f) bind g            == m bind (a -> f(a) bind g)

multiply(Return(m))         == m
multiply(map(Return, m))    == m
multiply(multiply(t))       == multiply(map(multiply, t))
```

Equalities mean **equal results under `run()`**. These values contain
functions, so comparing their Kotlin object identity is not a monad law.
Thunk side effects can be observed and replayed; monad/functor laws are
specified for **pure continuations**.

See
[`TrampolineTest`](../mathcat-core/src/commonTest/kotlin/org/evoleq/mathcat/core/trampoline/TrampolineTest.kt):
it is both executable documentation and a regression suite for the functor
and monad laws, laziness, error propagation and **50,000-step** recursion,
bind and multiply stress tests across supported commonTest platforms.

### Boundaries of the guarantee

- **Stack-safe, not constant heap:** a deeply left-associated bind chain
  needs a heap-allocated continuation stack. Time and memory are linear in
  the evaluated program's steps/deferred continuations.
- **Not async:** `More` suspends a pure computation via a *regular* lambda.
  It is not `suspend`, a coroutine scheduler, a timeout or a cancellation
  mechanism.
- **Not a graph resolver:** plugin dependency ordering needs explicit cycle
  detection, topological sorting and compatibility checks. A trampoline may
  help express recursive computations but does not replace those algorithms.
- **No memoization or exception coercion:** repeated `run()` can replay the
  thunks, and exceptions from user code propagate to the caller.
