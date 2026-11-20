package evoleq.toolkit.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import evoleq.toolkit.types.Sum2
import kotlin.coroutines.cancellation.CancellationException

/**
 * The manifest can be refreshed independently of the host application. It
 * carries platform-neutral metadata, not executable code or live capabilities.
 */
fun interface PluginCatalog {
    suspend fun snapshot(): PluginManifest
}

private val manifestFormat = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

/** Read the same stable JSON schema on JVM, JS, Native and Wasm. */
@Suppress("FunctionName")
fun ParseManifest(text: String): PluginManifest =
    manifestFormat.decodeFromString(text)

fun PluginManifest.toJson(): String = manifestFormat.encodeToString(this)

/**
 * Effectful discovery followed by PURE validation / dependency planning.
 *
 * Discover(catalog, target, host) * Activate(loader, target, host)
 *
 * This is a Kleisli composition. A fresh catalog snapshot is read for each
 * invocation: a new plugin may appear without rebuilding the host binary.
 */
@Suppress("FunctionName")
fun Discover(
    catalog: PluginCatalog,
    target: PluginTarget,
    host: PluginHostContext
): PluginStep<Set<PluginId>, PluginPlan> =
    PluginStep { selected ->
        try {
            Plan(catalog.snapshot(), selected, target, host)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Sum2.Left(PluginFailure.CatalogFailed(error.message ?: error.toString()))
        }
    }
