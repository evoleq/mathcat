package org.evoleq.mathcat.plugins.jvm

import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.test.runTest
import org.evoleq.mathcat.plugins.*
import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The service descriptor is packaged into a real temporary JAR. */
class FixtureJarPlugin : JvmPluginEntrypoint {
    override val id: String = "fixture"
    override val version: String = "1.0.0"
    override suspend fun activate(host: PluginHostContext): PluginSession {
        require(host.hostApi == 1)
        return PluginSession { }
    }
}

class JarPluginLoaderTest {
    @Test
    fun discoversJvmServiceFromVerifiedJarAndUnloadsOnStop() = runTest {
        val path = Files.createTempFile("mathcat-plugin-fixture", ".jar")
        try {
            val entry = "META-INF/services/" + JvmPluginEntrypoint::class.java.name
            JarOutputStream(Files.newOutputStream(path)).use { jar ->
                jar.putNextEntry(JarEntry(entry))
                jar.write((FixtureJarPlugin::class.java.name + "\n").encodeToByteArray())
                jar.closeEntry()
            }

            val digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path))
                .joinToString("") { b ->
                    (b.toInt() and 255).toString(16).padStart(2, '0')
                }
            val plugin = PluginDescriptor(
                id = PluginId("fixture"),
                version = PluginVersion(1, 0, 0),
                hostApi = 1,
                artifacts = mapOf(PluginTarget.JVM to PluginArtifact(path.toString(), digest))
            )
            val context = PluginHostContext(1, emptySet())
            val pipeline = Resolve(PluginManifest(listOf(plugin)), PluginTarget.JVM, context) *
                Activate(JarPluginLoader(), PluginTarget.JVM, context)

            val deployment = assertIs<Sum2.Right<PluginDeployment>>(
                pipeline.run(setOf(plugin.id))
            ).value
            assertEquals(listOf(plugin.id), deployment.active)
            assertEquals(Sum2.Right(Unit), deployment.stop())

            val corrupt = plugin.copy(
                artifacts = mapOf(
                    PluginTarget.JVM to PluginArtifact(path.toString(), "0".repeat(64))
                )
            )
            val failed = (Resolve(
                PluginManifest(listOf(corrupt)), PluginTarget.JVM, context
            ) * Activate(JarPluginLoader(), PluginTarget.JVM, context))
                .run(setOf(corrupt.id))
            assertTrue(assertIs<Sum2.Left<PluginFailure>>(failed).value
                is PluginFailure.LoadingFailed)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
