package org.evoleq.mathcat.plugins.js

import kotlinx.coroutines.await
import kotlin.js.Promise
import org.evoleq.mathcat.plugins.PluginCatalog
import org.evoleq.mathcat.plugins.PluginManifest
import org.evoleq.mathcat.plugins.ParseManifest

/**
 * Fetch an independently deployed plugin-manifest.json on every discovery.
 * 'no-cache' asks the browser to revalidate rather than reusing stale data.
 * Immutable, content-hashed JS chunks should use long-lived caching instead.
 *
 * Only trusted origins/manifests must be used; this does not verify signatures.
 */
class HttpPluginCatalog(private val url: String) : PluginCatalog {
    override suspend fun snapshot(): PluginManifest {
        val request = js("fetch(url, { cache: 'no-cache' })")
            .unsafeCast<Promise<dynamic>>()
        val response = request.await()
        if (response.ok != true) {
            error("Plugin manifest HTTP request failed: " + response.status)
        }
        val raw = response.text().unsafeCast<Promise<String>>().await()
        return ParseManifest(raw)
    }
}
