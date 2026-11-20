package org.evoleq.mathcat.core.trampoline

/**
 * A deferred, stack-safe computation. The mathematical presentation is
 * T(A) = A + (() -> T(A)): a value or one further suspended step.
 *
 * An internal Binding node makes arbitrary compositions of map/multiply
 * stack-safe too; the interpreter, not the platform call stack, evaluates them.
 * The thunk is lazy and may be executed again on a second run: no memoization.
 */
sealed interface Trampoline<out A> {
    data class Done<out A>(val value: A) : Trampoline<A>
    data class More<out A>(val next: () -> Trampoline<A>) : Trampoline<A>
}

/** Internal implementation detail: stack-safe functor/monad composition. */
internal data class Binding<X, out A>(
    val source: Trampoline<X>,
    val next: (X) -> Trampoline<A>
) : Trampoline<A>

/** Monad unit eta: A -> T(A). Matches MathCat's standalone State.Return. */
@Suppress("FunctionName")
fun <A> Return(value: A): Trampoline<A> = Trampoline.Done(value)

/** Suspend a step without evaluating its thunk. */
@Suppress("FunctionName")
fun <A> More(next: () -> Trampoline<A>): Trampoline<A> = Trampoline.More(next)

/** Covariant functor mapping, preserving laziness and stack safety. */
fun <A, B> Trampoline<A>.map(f: (A) -> B): Trampoline<B> =
    Binding(this) { value -> Return(f(value)) }

/** Monad multiplication (join): mu: T(T(A)) -> T(A). Does not run either layer. */
fun <A> Trampoline<Trampoline<A>>.multiply(): Trampoline<A> =
    Binding(this) { it }

/** Kleisli bind is *derived* from map and multiply, not an independent primitive. */
infix fun <A, B> Trampoline<A>.bind(
    f: (A) -> Trampoline<B>
): Trampoline<B> = map(f).multiply()

/** The familiar Evoleq left-to-right composition shorthand. */
operator fun <A, B> Trampoline<A>.times(
    next: (A) -> Trampoline<B>
): Trampoline<B> = bind(next)

/**
 * Interpret the suspended computation using an explicit continuation stack.
 * No recursive calls, including through map, multiply or bind.
 * Any exception thrown by user thunks/continuations propagates normally.
 */
@Suppress("UNCHECKED_CAST")
fun <A> Trampoline<A>.run(): A {
    var current: Trampoline<Any?> = this
    val pending = ArrayDeque<(Any?) -> Trampoline<Any?>>()

    while (true) {
        when (val node = current) {
            is Trampoline.Done -> {
                if (pending.isEmpty()) return node.value as A
                current = pending.removeLast()(node.value)
            }
            is Trampoline.More -> current = node.next()
            is Binding<*, *> -> {
                pending.add(node.next as ((Any?) -> Trampoline<Any?>))
                current = node.source
            }
        }
    }
}
