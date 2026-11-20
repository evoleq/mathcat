package evoleq.toolkit.plugins.jvm

import evoleq.toolkit.plugins.*
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.ServiceLoader

/**
 * Service provider implemented by a plugin JAR. The interface and its argument
 * classes MUST come from the host's shared plugin API classloader.
 *
 * META-INF/services/evoleq.toolkit.plugins.jvm.JvmPluginEntrypoint
 * contains the fully qualified provider class name.
 */
interface JvmPluginEntrypoint {
    val id: String
    val version: String
    suspend fun activate(host: PluginHostContext): PluginSession
}

/**
 * An actual JVM JAR loader: checks SHA-256 before executing plugin code,
 * discovers providers through ServiceLoader, and closes the URLClassLoader
 * when the deployment is stopped.
 *
 * The parent-first classloader is NOT a sandbox. Use trusted plugins only.
 * The host must also retain immutable JAR files while they are in use.
 */
class JarPluginLoader(
    private val parent: ClassLoader = JvmPluginEntrypoint::class.java.classLoader
) : PluginLoader {
    override suspend fun load(
        plugin: PluginDescriptor,
        artifact: PluginArtifact
    ): PluginHandle {
        val expected = requireNotNull(artifact.sha256) {
            "JVM JAR plugins require a sha256 digest in their artifact declaration"
        }
        val jar = if (artifact.uri.startsWith("file:")) File(URI(artifact.uri))
            else File(artifact.uri)
        require(jar.isFile) { "Plugin artifact is not a local file: ${artifact.uri}" }
        require(jar.extension.equals("jar", ignoreCase = true)) { "Expected .jar artifact" }

        val actual = sha256Of(jar)
        require(actual.equals(expected, ignoreCase = true)) {
            "SHA-256 mismatch for ${plugin.id}: expected $expected, found $actual"
        }

        val classLoader = URLClassLoader(arrayOf(jar.toURI().toURL()), parent)
        try {
            val entry = ServiceLoader.load(JvmPluginEntrypoint::class.java, classLoader)
                .iterator().asSequence().firstOrNull {
                    it.id == plugin.id.value && it.version == plugin.version.toString()
                } ?: error("No matching JvmPluginEntrypoint for ${plugin.id}@${plugin.version}")

            return object : PluginHandle {
                override suspend fun activate(host: PluginHostContext): PluginSession =
                    entry.activate(host)

                override suspend fun unload() {
                    classLoader.close()
                }
            }
        } catch (failure: Throwable) {
            classLoader.close()
            throw failure
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val chunk = ByteArray(8192)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                digest.update(chunk, 0, n)
            }
        }
        return digest.digest().joinToString("") { byte ->
            ((byte.toInt() and 0xff).toString(16)).padStart(2, '0')
        }
    }
}
