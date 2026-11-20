package evoleq.toolkit.transform

import java.util.UUID
import evoleq.toolkit.context.Contextual
import evoleq.toolkit.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TransformTest {
    @Test fun transformsResultSuccessAndPreservesFailure() {
        val f = Transform<Int, Int> { it + 2 }
        assertEquals(Result.Success(5), f.apply(Result.Success(3)))
        val failure = Result.Failure.Message("not allowed")
        assertSame(failure, f.apply(failure))
    }

    @Test fun contextualTransformPreservesUserIdAndContext() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val value = Contextual(id, "tenant", "ada")
        val f = TransformContextual<String, String> { it.uppercase() }
        assertEquals(Result.Success(Contextual(id, "tenant", "ADA")),
            f.apply(Result.Success(value)))
    }
}
