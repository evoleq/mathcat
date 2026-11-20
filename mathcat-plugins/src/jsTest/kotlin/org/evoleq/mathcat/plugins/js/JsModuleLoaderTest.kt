package org.evoleq.mathcat.plugins.js

import kotlinx.coroutines.test.runTest
import org.evoleq.mathcat.plugins.*
import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsModuleLoaderTest {
    @Test
    fun importsAnActualIndependentEsModuleAndRunsItsLifecycle() = runTest {
        // Node supports data: ES modules. This verifies the real dynamic import()
        // path instead of mocking the module import seam.
        val uri = "data:text/javascript," +
            "export%20const%20id%3D%22fixture%22%3B" +
            "export%20const%20version%3D%221.0.0%22%3B" +
            "export%20function%20activate(host)%7B" +
            "if(host.hostApi!%3D%3D1)throw%20Error(%22invalid%20host%22)%3B" +
            "return%20%7Bdispose()%7B%7D%7D%7D"
        val entry = PluginDescriptor(
            PluginId("fixture"),
            PluginVersion(1, 0, 0),
            hostApi = 1,
            artifacts = mapOf(PluginTarget.JS to PluginArtifact(uri))
        )
        val context = PluginHostContext(1, setOf("navigation"), mapOf("containerId" to "app"))
        val runtime = Resolve(PluginManifest(listOf(entry)), PluginTarget.JS, context) *
            Activate(JsModuleLoader(), PluginTarget.JS, context)
        val deployment = assertIs<Sum2.Right<PluginDeployment>>(
            runtime.run(setOf(entry.id))
        ).value

        assertEquals(listOf(entry.id), deployment.active)
        assertEquals(Sum2.Right(Unit), deployment.stop())
    }
    @Test
    fun importedModuleCanInvokeScopedActionAndNavigationCallbacks() = runTest {
        val events = mutableListOf<String>()
        val plugin = PluginId("bridge")
        val callbacks = object : PluginCallbacks {
            override suspend fun dispatchAction(plugin: PluginId, actionKey: String): Boolean {
                events += "action:${plugin.value}:$" + "{actionKey}"
                return plugin == PluginId("bridge") && actionKey == "shares.refresh"
            }

            override suspend fun navigate(plugin: PluginId, destinationKey: String): Boolean {
                events += "route:${plugin.value}:$" + "{destinationKey}"
                return plugin == PluginId("bridge") && destinationKey == "shares.home"
            }
        }
        // The data: URI is a separately imported actual ES module, not
        // linked Kotlin code. The imported code awaits the JS Promise bridge.
        val script = """
            export const id = "bridge";
            export const version = "1.0.0";
            export async function activate(host) {
                if (!host.actions || !host.navigation) throw Error("missing capabilities");
                const accepted = await host.actions.dispatch("shares.refresh");
                const moved = await host.navigation.navigate("shares.home");
                const denied = await host.actions.dispatch("admin.delete");
                if (!accepted || !moved || denied) throw Error("incorrect command results");
                return { dispose() {} };
            }
        """.trimIndent()
        val uri = "data:text/javascript," +
            script.encodeToByteArray().joinToString("") { byte ->
                "%" + (byte.toInt() and 255).toString(16).padStart(2, '0')
            }
        val descriptor = PluginDescriptor(
            id = plugin,
            version = PluginVersion(1, 0, 0),
            hostApi = 1,
            requiredCapabilities = setOf("actions", "navigation"),
            artifacts = mapOf(PluginTarget.JS to PluginArtifact(uri))
        )
        val context = PluginHostContext(1, setOf("actions", "navigation"))
        val loader = JsModuleLoader(
            callbacks = callbacks,
            callbackScope = this
        )
        val pipeline = Resolve(PluginManifest(listOf(descriptor)), PluginTarget.JS, context) *
            Activate(loader, PluginTarget.JS, context)

        val deployment = assertIs<Sum2.Right<PluginDeployment>>(
            pipeline.run(setOf(plugin))
        ).value
        assertEquals(
            listOf(
                "action:bridge:shares.refresh",
                "route:bridge:shares.home",
                "action:bridge:admin.delete"
            ),
            events
        )
        assertEquals(Sum2.Right(Unit), deployment.stop())
    }

}
