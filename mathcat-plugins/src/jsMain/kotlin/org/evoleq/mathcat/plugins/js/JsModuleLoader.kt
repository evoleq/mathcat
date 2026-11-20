package org.evoleq.mathcat.plugins.js

import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.jsTypeOf
import org.evoleq.mathcat.plugins.*

/**
 * The JS ABI is deliberately plain JavaScript, not Kotlin/JS compiler ABI:
 *
 * export const id = "banking";
 * export const version = "1.0.0";
 * export async function activate(host) {
 *   // host: { hostApi: number, capabilities: string[], properties: object }
 *   return { async dispose() { ... } };
 * }
 *
 * Dynamic import() works in modern browsers and Node. The URL must point
 * to an immutable ES module. Runtime isolation is NOT provided by import().
 */
class JsModuleLoader(
    private val importer: suspend (String) -> dynamic = ::dynamicImport
) : PluginLoader {
    override suspend fun load(
        plugin: PluginDescriptor,
        artifact: PluginArtifact
    ): PluginHandle {
        require(artifact.sha256 == null) {
            "JS SHA-256 verification is not implemented. Use immutable hashed URLs; " +
                "do not claim cryptographic integrity from a filename."
        }

        val module = importer(artifact.uri)
        require(module != null && jsTypeOf(module.activate) == "function") {
            "JS plugin must export activate(host)"
        }
        require(module.id == plugin.id.value && module.version == plugin.version.toString()) {
            "JS module identity/version does not match the manifest for ${plugin.id}"
        }

        return object : PluginHandle {
            override suspend fun activate(host: PluginHostContext): PluginSession {
                val hostData = plainHost(host)
                val session = awaitValue(module.activate(hostData))
                require(session != null && jsTypeOf(session.dispose) == "function") {
                    "JS plugin activate(host) must return an object with dispose()"
                }
                return PluginSession {
                    awaitValue(session.dispose())
                    Unit
                }
            }

            override suspend fun unload() {
                // Browser/Node ES module namespaces cannot be unloaded reliably.
                // Dispose must release DOM, listeners, subscriptions and resources.
            }
        }
    }
}

private suspend fun dynamicImport(uri: String): dynamic {
    val promise = js("import(/* webpackIgnore: true */ uri)").unsafeCast<Promise<dynamic>>()
    return promise.await()
}

private suspend fun awaitValue(value: dynamic): dynamic {
    val promise = js("Promise.resolve(value)").unsafeCast<Promise<dynamic>>()
    return promise.await()
}

private fun plainHost(host: PluginHostContext): dynamic {
    val payload = js("({})")
    payload.hostApi = host.hostApi
    payload.capabilities = host.capabilities.toTypedArray()
    val properties = js("({})")
    for ((key, value) in host.properties) {
        properties[key] = value
    }
    payload.properties = properties
    return payload
}
