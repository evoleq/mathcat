package org.evoleq.mathcat.plugins.jvm

import java.nio.file.Files
import java.nio.file.Path
import org.evoleq.mathcat.plugins.PluginCatalog
import org.evoleq.mathcat.plugins.PluginManifest
import org.evoleq.mathcat.plugins.ParseManifest

/** Read a fresh, externally deployed manifest on each invocation. */
class FilePluginCatalog(private val path: Path) : PluginCatalog {
    override suspend fun snapshot(): PluginManifest =
        ParseManifest(Files.readString(path))
}
