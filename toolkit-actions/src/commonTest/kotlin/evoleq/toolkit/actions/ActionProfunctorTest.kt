package evoleq.toolkit.actions

import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.writer.Writer
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionProfunctorTest {
    data class State(val input: Int, val output: String = "", val verified: String = "")
    private val original = Action(
        name = "example",
        reader = Reader<State, Int> { it.input },
        writer = Writer<State, String> { message -> { s -> s.copy(output = message) } },
        onSuccess = Writer<State, String> { message -> { s -> s.copy(verified = message) } }
    )
    private val state = State(6)

    private fun <I, O> observable(action: Action<State, I, O>, input: O): Pair<State, State> =
        action.writer(input)(state) to action.onSuccess!!(input)(state)

    @Test
    fun identityLawsHoldForReaderAndBothWriters() {
        val transformed = original.dimap({ s: String -> s }, { n: Int -> n })
        assertEquals(original.reader(state), transformed.reader(state))
        assertEquals(observable(original, "hello"), observable(transformed, "hello"))
        assertEquals("example", transformed.name)
    }

    @Test
    fun mappingCompositionAndDimapAgreeInBothDirections() {
        val before1: (Int) -> String = { it.toString() }
        val before2: (Long) -> Int = { it.toInt() }
        val after1: (Int) -> String = { "input=$it" }
        val after2: (String) -> Int = { it.length }

        val sequential = original
            .dimap(before1, after1)
            .dimap(before2, after2)
        val combined = original.dimap(
            before = { value: Long -> before1(before2(value)) },
            after = { input: Int -> after2(after1(input)) }
        )

        assertEquals(sequential.reader(state), combined.reader(state))
        assertEquals(observable(sequential, 42L), observable(combined, 42L))
        assertEquals(sequential.name, combined.name)
    }

    @Test
    fun mapAndContraMapAffectTheCorrectSides() {
        val mapped = original.map { it * 3 }
        assertEquals(18, mapped.reader(state))
        assertEquals(state.copy(output = "ok"), mapped.writer("ok")(state))

        val contramapped = original.contraMap { value: Int -> "code:$value" }
        assertEquals(6, contramapped.reader(state))
        assertEquals(state.copy(output = "code:7"), contramapped.writer(7)(state))
        assertEquals(state.copy(verified = "code:7"), contramapped.onSuccess!!(7)(state))
    }
}
