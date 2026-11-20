package evoleq.toolkit.core.writer

/**
 * A pure, curried state updater. The first argument is an update command, and the
 * resulting function transforms a state of type [S].
 *
 * In contrast to the traditional Writer monad, this Writer means a state updater.
 * It is deliberately NOT claimed to be a HKT or a Monad instance.
 */
fun interface Writer<S, in A> {
    operator fun invoke(value: A): (S) -> S
}

/** Contravariant mapping of the input to a state updater. */
fun <S, A, B> Writer<S, A>.contraMap(f: (B) -> A): Writer<S, B> =
    Writer { value -> this(f(value)) }

/** Apply an update command to a concrete state. */
fun <S, A> Writer<S, A>.write(value: A, to: S): S = this(value)(to)
