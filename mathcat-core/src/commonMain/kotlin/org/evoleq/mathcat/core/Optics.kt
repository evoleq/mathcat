package org.evoleq.mathcat.core

/**
 * Read and write access without assuming any Lens law.
 *
 * This structure is closed under composition. The get/set functions may nevertheless
 * be partial or have application-specific behavior, so using this type alone does
 * not imply Get-Put, Put-Get or Put-Put.
 */
data class Selection<S, A>(
    val get: Reader<S, A>,
    val set: Writer<S, A>
)

/**
 * A Lens is a Selection whose implementation MUST satisfy:
 *
 * 1. Get-Put: set(get(s))(s) == s
 * 2. Put-Get: get(set(a)(s)) == a
 * 3. Put-Put: set(b)(set(a)(s)) == set(b)(s)
 *
 * Kotlin cannot prove these laws. They are contractual obligations of constructors
 * and clients; tests should demonstrate them on representative domains.
 */
data class Lens<S, A>(
    val get: Reader<S, A>,
    val set: Writer<S, A>
) {
    fun asSelection(): Selection<S, A> = Selection(get, set)
}

/**
 * Identity Lens on S: get = id_S and set(a)(s) = a.
 *
 * This is the identity for lawful Lens composition; unlike arbitrary Lens
 * constructors, it satisfies all three laws by construction.
 */
@Suppress("FunctionName")
fun <S> IdLens(): Lens<S, S> =
    Lens(
        get = Reader { it },
        set = Writer { newValue -> { _ -> newValue } }
    )

/** Compose lawful lenses: the result is lawful when both inputs are lawful. */
operator fun <S, A, B> Lens<S, A>.times(next: Lens<A, B>): Lens<S, B> =
    Lens(
        get = Reader { source -> next.get(get(source)) },
        set = Writer { value ->
            { source ->
                set(next.set(value)(get(source)))(source)
            }
        }
    )

/** Compose arbitrary selections: preserve operations, make no lawfulness claim. */
operator fun <S, A, B> Selection<S, A>.times(
    next: Selection<A, B>
): Selection<S, B> =
    Selection(
        get = Reader { source -> next.get(get(source)) },
        set = Writer { value ->
            { source ->
                set(next.set(value)(get(source)))(source)
            }
        }
    )

/** A lawful outer Lens does not make an arbitrary inner Selection lawful. */
operator fun <S, A, B> Lens<S, A>.times(
    next: Selection<A, B>
): Selection<S, B> = asSelection() * next

/** An arbitrary outer Selection does not make an inner Lens lawful. */
operator fun <S, A, B> Selection<S, A>.times(
    next: Lens<A, B>
): Selection<S, B> = this * next.asSelection()

/** Read through a Selection. */
operator fun <S, A, B> Selection<S, A>.times(reader: Reader<A, B>): Reader<S, B> =
    Reader { source -> reader(get(source)) }

/** Lift a Writer on the focused part into a Writer on the entire state. */
operator fun <S, A, B> Selection<S, A>.times(writer: Writer<A, B>): Writer<S, B> =
    Writer { value ->
        { source ->
            set(writer(value)(get(source)))(source)
        }
    }

/** The same operations are available for lawful Lenses, with Reader/Writer output. */
operator fun <S, A, B> Lens<S, A>.times(reader: Reader<A, B>): Reader<S, B> =
    asSelection() * reader

operator fun <S, A, B> Lens<S, A>.times(writer: Writer<A, B>): Writer<S, B> =
    asSelection() * writer

/** Named alternatives make intent explicit at call sites. */
fun <S, A, B> Selection<S, A>.timesR(reader: Reader<A, B>): Reader<S, B> = this * reader
fun <S, A, B> Selection<S, A>.timesW(writer: Writer<A, B>): Writer<S, B> = this * writer
fun <S, A, B> Lens<S, A>.timesR(reader: Reader<A, B>): Reader<S, B> = this * reader
fun <S, A, B> Lens<S, A>.timesW(writer: Writer<A, B>): Writer<S, B> = this * writer

/** 
 * Filter/replace selection, deliberately NOT a lawful Lens:
 * replacing the selected values with themselves may reorder a list.
 */
@Suppress("FunctionName")
fun <A> FilterBy(predicate: (A) -> Boolean): Selection<List<A>, List<A>> =
    Selection(
        get = Reader { source -> source.filter(predicate) },
        set = Writer { replacement ->
            { source -> source.filterNot(predicate) + replacement }
        }
    )
