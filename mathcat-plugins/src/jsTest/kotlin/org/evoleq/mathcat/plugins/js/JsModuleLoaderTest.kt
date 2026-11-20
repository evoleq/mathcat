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
}
