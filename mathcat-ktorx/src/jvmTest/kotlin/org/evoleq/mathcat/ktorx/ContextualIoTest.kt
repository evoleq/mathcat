package org.evoleq.mathcat.ktorx

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.routing
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.response.respondText
import io.ktor.server.application.call
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.evoleq.mathcat.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.UUID

/**
 * These tests teach the Solyton-style Receive * Transform * Respond DSL,
 * while exercising actual Ktor requests, including authenticated context.
 */
class ContextualIoTest {
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000042")

    /** Simple Result serializer used here until the serialization PR is integrated. */
    private val codec = object : KSerializer<Result<String>> {
        override val descriptor = buildClassSerialDescriptor("Result") {
            element<String>("type")
            element<String>("data", isOptional = true)
            element<String>("message", isOptional = true)
        }
        override fun serialize(encoder: Encoder, value: Result<String>) {
            val c = encoder.beginStructure(descriptor)
            when (value) {
                is Result.Success -> {
                    c.encodeStringElement(descriptor, 0, "Success")
                    c.encodeStringElement(descriptor, 1, value.data)
                }
                is Result.Failure.Message -> {
                    c.encodeStringElement(descriptor, 0, "Failure")
                    c.encodeStringElement(descriptor, 2, value.value)
                }
                is Result.Failure.Exception -> {
                    c.encodeStringElement(descriptor, 0, "Failure")
                    c.encodeStringElement(descriptor, 2, value.value.message ?: "error")
                }
                is Result.Failure.HttpStatusMessage -> {
                    c.encodeStringElement(descriptor, 0, "Failure")
                    c.encodeStringElement(descriptor, 2, value.value)
                }
            }
            c.endStructure(descriptor)
        }
        override fun deserialize(decoder: Decoder): Result<String> {
            val c = decoder.beginStructure(descriptor)
            var t = ""
            var data = ""
            var message = ""
            while (true) {
                when (c.decodeElementIndex(descriptor)) {
                    0 -> t = c.decodeStringElement(descriptor, 0)
                    1 -> data = c.decodeStringElement(descriptor, 1)
                    2 -> message = c.decodeStringElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unknown index")
                }
            }
            c.endStructure(descriptor)
            return if (t == "Success") Result.Success(data)
                else Result.Failure.Message(message)
        }
    }

    @Test
    fun receiveTransformRespondComposesIntoARequestLocalAction() = testApplication {
        application {
            routing {
                post("/echo") {
                    val workflow = Receive<Unit, String>(String.serializer()) *
                        Transform<String, String> { it.uppercase() } *
                        Respond(codec)
                    workflow runOn RequestBase(call, Unit)
                }
            }
        }
        val result = client.post("/echo") {
            headers.append(HttpHeaders.ContentType, "application/json")
            setBody("\"hello\"")
        }
        assertEquals(HttpStatusCode.OK, result.status)
        assertTrue(result.bodyAsText().contains("HELLO"))
    }

    @Test
    fun contextualQueryAndTransformPreserveIdentityAndContext() = testApplication {
        val identity: suspend (io.ktor.server.application.ApplicationCall) ->
            Result<RequestIdentity> = {
                Result.Success(RequestIdentity(id, "tenant-42"))
            }
        application {
            routing {
                get("/names") {
                    val workflow = ReceiveContextual<Unit, String>(
                        params = { params -> params["name"] ?: "" },
                        identity = identity
                    ) * TransformContextual<String, String> {
                        it.uppercase()
                    }
                    val outcome = workflow runOn RequestBase(call, Unit)
                    val context = (outcome as Result.Success).data
                    assertEquals(id, context.userId)
                    assertEquals("tenant-42", context.context)
                    call.respondText(context.data)
                }
            }
        }
        assertEquals("ADA", client.get("/names?name=ada").bodyAsText())
    }

    @Test
    fun authenticatedFailureSkipsBodyReaderAndTransform() = testApplication {
        application {
            routing {
                post("/protected") {
                    val workflow = ReceiveContextual<Unit, String>(
                        serializer = String.serializer(),
                        identity = {
                            Result.Failure.HttpStatusMessage(401, "Unauthorized")
                        }
                    ) * TransformContextual<String, String> { error("Must not execute") } *
                        Respond(object : KSerializer<Result<Contextual<String>>> {
                            override val descriptor = buildClassSerialDescriptor("AuthenticatedResult") {
                                element<String>("type")
                                element<String>("message", isOptional = true)
                            }
                            override fun serialize(encoder: Encoder, value: Result<Contextual<String>>) {
                                val c = encoder.beginStructure(descriptor)
                                c.encodeStringElement(descriptor, 0, "Failure")
                                c.encodeStringElement(descriptor, 1, (value as Result.Failure.HttpStatusMessage).value)
                                c.endStructure(descriptor)
                            }
                            override fun deserialize(decoder: Decoder): Result<Contextual<String>> =
                                error("Not used in test")
                        })
                    workflow runOn RequestBase(call, Unit)
                }
            }
        }
        val result = client.post("/protected")
        assertEquals(HttpStatusCode.Unauthorized, result.status)
        assertTrue(result.bodyAsText().contains("Unauthorized"))
    }

    @Test
    fun pureContextualTransformHasNoHttpDependencyOrSideEffects() {
        val value = Contextual(id, "tenant", 3)
        val transformed = TransformContextual<Int, String> { it.toString() }
            .apply(Result.Success(value))
        assertEquals(Result.Success(Contextual(id, "tenant", "3")), transformed)
    }
}
