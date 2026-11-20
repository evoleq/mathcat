package evoleq.toolkit.plugins.jvm

import java.nio.file.Files
import java.nio.file.Path
import evoleq.toolkit.plugins.PluginCatalog
import evoleq.toolkit.plugins.PluginManifest
import evoleq.toolkit.plugins.ParseManifest

/** Read a fresh, externally deployed manifest on each invocation. */
class FilePluginCatalog(private val path: Path) : PluginCatalog {
    override suspend fun snapshot(): PluginManifest =
        ParseManifest(Files.readString(path))
}
