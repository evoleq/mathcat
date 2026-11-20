package evoleq.toolkit.middleware

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import evoleq.toolkit.actions.Action
import evoleq.toolkit.actions.ActionDispatcher
import evoleq.toolkit.actions.ActionEnvelope
import evoleq.toolkit.api.Api
import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.storage.Storage
import evoleq.toolkit.core.writer.Writer
import evoleq.toolkit.ktorx.ApiClient
import evoleq.toolkit.ktorx.ApiClientResolver
import evoleq.toolkit.ktorx.context.data.Contextual
import evoleq.toolkit.result.Result
import evoleq.toolkit.serialization.ResultSerializer
import evoleq.toolkit.serialization.Serializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MiddlewarePipelineTest {
    private class Key
    private data class App(
        val input: String,
        val output: String = "",
        val verified: String = "",
        val context: String = ""
    )

    private val serializers = Serializers { add<String>(String.serializer()) }
    private val api = Api { post<String, String>(Key::class, "call") }
    private val baseAction = Action(
        name = "call",
        reader = Reader<App, String> { it.input },
        writer = Writer<App, String> { value -> { state -> state.copy(output = value) } },
        onSuccess = Writer<App, String> { value -> { state ->
            state.copy(verified = "confirmed:$value")
        } }
    )
    private val bound = baseAction.at(Key::class)

    private fun response(result: Result<String>): String =
        Json.encodeToString(ResultSerializer(String.serializer()), result)

    @Test
    fun composedStagesApplyContextWriterReactionAndKeepOneInitialSnapshot() = runTest {
        val client = HttpClient(MockEngine { respond(response(Result.Success("done"))) })
        try {
            var current = App("request")
            var reads = 0
            val storage = Storage(
                read = { reads += 1; current },
                write = { current = it }
            )
            val runtime = MiddlewareRuntime(storage, ApiClientResolver<App> { state ->
                assertEquals("request", state.input)
                ApiClient(client, api, "https://example.test", serializers)
            })
            val events = mutableListOf<String>()
            val stages = Read(bound) *
                Call(bound) *
                OnError(bound) *
                Dispatch(bound) { context, runtimeOfStage ->
                    events += "context"
                    runtimeOfStage.storage.write(
                        runtimeOfStage.storage.read().copy(context = context)
                    )
                } *
                React(bound) { value, _ -> events += "react:$value" }

            assertEquals(Result.Success("done"), stages runOn runtime)
            assertEquals(App("request", "done", "confirmed:done", ""), current)
            assertEquals(listOf("context", "react:done"), events)
            // One read for the pipeline snapshot, three for the explicit
            // read-modify-write stages; no second Reader read in Call.
            assertEquals(4, reads)
        } finally {
            client.close()
        }
    }

    @Test
    fun errorPolicyRunsBeforeDispatchAndPreventsSuccessReactions() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                response(Result.Failure.Message("not allowed")),
                status = HttpStatusCode.Forbidden
            )
        })
        try {
            var current = App("request")
            val storage = Storage(
                read = { current },
                write = { current = it }
            )
            var failures = 0
            var reactions = 0
            val runtime = MiddlewareRuntime(storage, ApiClientResolver<App> {
                ApiClient(client, api, "https://example.test", serializers)
            })
            val policy = FailurePolicy<App> { failure, snapshot ->
                failures += 1
                assertEquals("request", snapshot.input)
                assertEquals(Result.Failure.HttpStatusMessage(403, "not allowed"), failure)
                Result.Failure.Message("display-safe error")
            }
            val pipeline = ProcessAction(
                action = bound,
                call = Call(bound),
                policy = policy,
                onSuccess = { _, _ -> reactions += 1 }
            )
            assertEquals(Result.Failure.Message("display-safe error"), pipeline runOn runtime)
            assertEquals(App("request"), current)
            assertEquals(1, failures)
            assertEquals(0, reactions)
        } finally {
            client.close()
        }
    }

    @Test
    fun envelopeFollowUpsAreLazyOrderedSuccessOnlyAndPreserveMetadata() = runTest {
        val child = ActionEnvelope(
            action = baseAction,
            id = "child",
            parentId = "root",
            meta = mapOf("source" to "test")
        )
        var deferred = 0
        val parent = ActionEnvelope(
            action = baseAction,
            id = "root",
            next = listOf(child),
            nextLazy = listOf {
                deferred += 1
                child.copy(id = "deferred")
            }
        )
        val received = mutableListOf<ActionEnvelope<App, *, *>>()
        val dispatcher = ActionDispatcher<App> { received += it }
        val runtime = MiddlewareRuntime(
            Storage(read = { App("request") }, write = { _: App -> }),
            ApiClientResolver<App> { error("ReactEnvelope must not resolve HTTP") }
        )
        val stage = ReactEnvelope(parent, dispatcher)
        assertEquals(
            Result.Failure.Message("fail"),
            stage.runOn(runtime, Result.Failure.Message("fail"))
        )
        assertEquals(0, deferred)
        assertEquals(emptyList(), received)

        assertEquals(Result.Success("ok"), stage.runOn(runtime, Result.Success("ok")))
        assertEquals(1, deferred)
        assertEquals(listOf("child", "deferred"), received.map { it.id })
        assertSame(child, received.first())
        assertEquals("root", received.first().parentId)
        assertEquals(mapOf("source" to "test"), received.first().meta)
    }
}
