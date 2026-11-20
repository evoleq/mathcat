package org.evoleq.mathcat.evolution.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.evoleq.mathcat.evolution.Flow

/**
 * Host-owned interpretation of a pure Flow.
 *
 * One consumer serializes state transitions. Each transition's command list
 * runs in a structured child, in list order; different transitions' command
 * groups may overlap. The caller owns the parent scope. Stop cancels only
 * this runtime's child job, never the caller's parent or a borrowed Store.
 */
class FlowRuntime<S, E : Any, C> internal constructor(
    private val flow: Flow<S, E, C>,
    initialState: S,
    parentScope: CoroutineScope,
    queueCapacity: Int,
    private val execute: suspend (C, suspend (E) -> Unit) -> Unit,
    private val onCommandFailure: ((C, Throwable) -> E)?
) {
    init {
        require(queueCapacity in 0..65_536) {
            "queueCapacity must be between 0 (rendezvous) and 65536"
        }
    }

    private val parentJob: Job = requireNotNull(parentScope.coroutineContext[Job]) {
        "Run requires a host-owned CoroutineScope containing a parent Job"
    }

    private val runtimeJob: Job = SupervisorJob(parentJob)
    private val runtimeScope = CoroutineScope(parentScope.coroutineContext + runtimeJob)
    private val inbox = Channel<E>(queueCapacity)
    private val snapshots = MutableStateFlow(initialState)

    /** Read-only observed state, independent of any Compose storage adapter. */
    val state: StateFlow<S> = snapshots.asStateFlow()

    private val consumer = runtimeScope.launch {
        try {
            for (event in inbox) {
                // The only writer of snapshots: no asynchronous effect may
                // mutate the state behind this transition function's back.
                val next = flow.evolve(snapshots.value, event)
                snapshots.value = next.state

                if (next.commands.isNotEmpty()) {
                    // Group commands run sequentially in this child.
                    // A slow effect must not block the event consumer.
                    runtimeScope.launch {
                        for (command in next.commands) {
                            try {
                                execute(command) { completion -> inbox.send(completion) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                // Explicit, opt-in mapping from an effect
                                // failure to the event algebra.
                                val eventFromError = onCommandFailure?.invoke(command, error)
                                if (eventFromError != null) {
                                    inbox.send(eventFromError)
                                } else {
                                    // Do not silently turn bugs into success.
                                    // SupervisorJob isolates other event work.
                                    throw error
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            // An unexpected reducer failure or parent cancellation must
            // prevent producers from submitting into a dead queue.
            inbox.close()
        }
    }

    /**
     * Fast, explicit backpressure. False means the buffer was full or the
     * runtime was closed; callers must decide whether to retry or reject.
     */
    fun offer(event: E): Boolean = inbox.trySend(event).isSuccess

    /** Suspends for capacity; throws if the runtime has stopped. */
    suspend fun send(event: E) {
        inbox.send(event)
    }

    /** Idempotent. Pending events may be dropped by cancellation. */
    fun stop() {
        inbox.close()
        runtimeJob.cancel()
    }
}

/**
 * Interpret a pure Evoleq Flow within a caller-owned structured scope.
 *
 * The execute callback receives a command and a reporter for typed completion
 * events. No global scope, timer polling, detached launch or implicit Store
 * lifecycle is created.
 */
@Suppress("FunctionName")
fun <S, E : Any, C> Run(
    flow: Flow<S, E, C>,
    initialState: S,
    scope: CoroutineScope,
    queueCapacity: Int = 64,
    onCommandFailure: ((C, Throwable) -> E)? = null,
    execute: suspend (C, suspend (E) -> Unit) -> Unit
): FlowRuntime<S, E, C> = FlowRuntime(
    flow = flow,
    initialState = initialState,
    parentScope = scope,
    queueCapacity = queueCapacity,
    execute = execute,
    onCommandFailure = onCommandFailure
)
