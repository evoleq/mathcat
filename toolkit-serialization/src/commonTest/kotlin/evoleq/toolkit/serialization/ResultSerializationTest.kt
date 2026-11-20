package evoleq.toolkit.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import evoleq.toolkit.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
private data class Login(val username: String, val password: String)

class ResultSerializationTest {
    private val codec = ResultSerializer(Login.serializer())

    @Test fun legacySuccessEnvelopeIsPreserved() {
        val expected = Result.Success(Login("alice", "pw"))
        val json = """{"type":"Success","classType":"Login","data":{"username":"alice","password":"pw"}}"""
        assertEquals(json, Json.encodeToString(codec, expected))
        assertEquals(expected, Json.decodeFromString(codec, json))
    }

    @Test fun legacyFailureEnvelopeIsPreserved() {
        val json = """{"type":"Failure","message":"not allowed"}"""
        val failed: Result<Login> = Result.Failure.Message("not allowed")
        assertEquals(json, Json.encodeToString(codec, failed))
        assertEquals(failed, Json.decodeFromString(codec, json))
    }

    @Test fun exceptionsAndHttpStatusFailuresRemainMessagesOnTheWire() {
        val exception: Result<Login> = Result.Failure.Exception(IllegalArgumentException("bad"))
        assertEquals("""{"type":"Failure","message":"bad"}""",
            Json.encodeToString(codec, exception))
        val http: Result<Login> = Result.Failure.HttpStatusMessage(403, "forbidden")
        assertEquals("""{"type":"Failure","message":"forbidden"}""",
            Json.encodeToString(codec, http))
        assertIs<Result.Failure.Message>(Json.decodeFromString(codec,
            """{"type":"Failure","message":"bad"}"""))
    }

    @Test fun genericPayloadsDoNotDependOnGlobalClassNameLookup() {
        val listCodec = ResultSerializer(ListSerializer(Login.serializer()))
        val original: Result<List<Login>> = Result.Success(listOf(Login("alice", "pw")))
        val encoded = Json.encodeToString(listCodec, original)
        assertEquals(original, Json.decodeFromString(listCodec, encoded))
    }

    @Test fun manualRegistryCanProvideThePayloadSerializer() {
        val registry = Serializers { add(Login::class, Login.serializer()) }
        val serializer = registry.resultSerializer(Login::class)
        val value: Result<Login> = Result.Success(Login("alice", "pw"))
        assertEquals(value, Json.decodeFromString(serializer,
            Json.encodeToString(serializer, value)))
    }
}
