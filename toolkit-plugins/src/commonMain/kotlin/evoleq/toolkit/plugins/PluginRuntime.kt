package evoleq.toolkit.plugins

import kotlin.coroutines.cancellation.CancellationException
import evoleq.toolkit.types.Sum2

/**
 * An effectful Kleisli arrow in the error sum:
 *
 * A -> suspend (PluginFailure + B).
 *
 * The operator composes from left to right and short-circuits on the left
 * summand, while the manifest and its dependency plan remain pure data.
 */
fun interface PluginStep<A, B> {
    suspend fun run(input: A): PluginResult<B>
}

operator fun <A, B, C> PluginStep<A, B>.times(
    next: PluginStep<B, C>
): PluginStep<A, C> = PluginStep { input ->
    when (val first = this.run(input)) {
        is Sum2.Left -> first
        is Sum2.Right -> next.run(first.value)
    }
}

@Suppress("FunctionName")
fun Resolve(
    manifest: PluginManifest,
    target: PluginTarget,
    host: PluginHostContext
): PluginStep<Set<PluginId>, PluginPlan> =
    PluginStep { selected -> Plan(manifest, selected, target, host) }

/** One dynamically loaded artifact; unloading is separate from deactivation. */
interface PluginHandle {
    suspend fun activate(host: PluginHostContext): PluginSession
    suspend fun unload()
}

/** An activated plugin, with explicit disposal of its contributions. */
fun interface PluginSession {
    suspend fun dispose()
}

/** Platform adapters own code loading; this common interface knows no JVM/JS APIs. */
fun interface PluginLoader {
    suspend fun load(plugin: PluginDescriptor, artifact: PluginArtifact): PluginHandle
}

internal data class ActivePlugin(
    val descriptor: PluginDescriptor,
    val handle: PluginHandle,
    val session: PluginSession?
)

/**
 * A deployment owns its sessions and loaded handles. Stop is idempotent
 * sequentially; concurrent calls and atomic rollback are not promised.
 */
class PluginDeployment internal constructor(private val entries: List<ActivePlugin>) {
    val active: List<PluginId> get() = entries.map { it.descriptor.id }
    private var stopped = false

    suspend fun stop(): PluginResult<Unit> {
        if (stopped) return Sum2.Right(Unit)
        stopped = true
        var firstError: PluginFailure? = null
        for (entry in entries.asReversed()) {
            try {
                entry.session?.dispose()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                if (firstError == null) firstError = PluginFailure.CleanupFailed(
                    entry.descriptor.id, error.message ?: error.toString()
                )
            }
        }
        for (entry in entries.asReversed()) {
            try {
                entry.handle.unload()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                if (firstError == null) firstError = PluginFailure.CleanupFailed(
                    entry.descriptor.id, error.message ?: error.toString()
                )
            }
        }
        return firstError?.let { Sum2.Left(it) } ?: Sum2.Right(Unit)
    }
}

/**
 * Activate an already verified pure plan. Load and activate happen in
 * dependency order; failed activation attempts best-effort reverse cleanup.
 * This is not a database transaction or a sandbox for untrusted code.
 */
@Suppress("FunctionName")
fun Activate(
    loader: PluginLoader,
    target: PluginTarget,
    host: PluginHostContext
): PluginStep<PluginPlan, PluginDeployment> = PluginStep { plan ->
    val opened = mutableListOf<ActivePlugin>()
    var phase = "load"
    var current: PluginId? = null

    try {
        for (plugin in plan.ordered) {
            current = plugin.id
            val artifact = plugin.artifacts[target]
                ?: return@PluginStep Sum2.Left(PluginFailure.MissingArtifact(plugin.id, target))
            phase = "load"
            val handle = loader.load(plugin, artifact)
            opened += ActivePlugin(plugin, handle, null)
            phase = "activate"
            val session = handle.activate(host)
            opened[opened.lastIndex] = ActivePlugin(plugin, handle, session)
        }
        Sum2.Right(PluginDeployment(opened.toList()))
    } catch (cancel: CancellationException) {
        cleanup(opened)
        throw cancel
    } catch (error: Throwable) {
        cleanup(opened)
        val id = requireNotNull(current)
        val message = error.message ?: error.toString()
        Sum2.Left(
            if (phase == "load") PluginFailure.LoadingFailed(id, message)
            else PluginFailure.ActivationFailed(id, message)
        )
    }
}

/** Best effort: never replace the original load/activation failure. */
private suspend fun cleanup(entries: List<ActivePlugin>) {
    for (entry in entries.asReversed()) {
        try {
            entry.session?.dispose()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            // Preserve the original activation failure.
        }
    }
    for (entry in entries.asReversed()) {
        try {
            entry.handle.unload()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            // Preserve the original activation failure.
        }
    }
}
