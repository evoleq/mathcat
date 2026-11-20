package evoleq.toolkit.plugins

import evoleq.toolkit.types.Sum2
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A portable description of a runtime plugin. The manifest is data, not
 * executable code and not an authorization mechanism.
 */
@Serializable
@JvmInline
value class PluginId(val value: String) {
    init {
        require(value.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]*"))) {
            "PluginId must be a nonempty portable identifier"
        }
    }

    override fun toString(): String = value
}

/** A deliberately small SemVer core (no prerelease/range resolution in v1). */
@Serializable
data class PluginVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<PluginVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0)
    }

    override fun compareTo(other: PluginVersion): Int =
        compareValuesBy(this, other, PluginVersion::major, PluginVersion::minor, PluginVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

@Serializable
enum class PluginTarget { JVM, JS }

/**
 * JS uses an immutable, content-hashed URL for cache busting. This alone is
 * NOT a cryptographic integrity check. JVM JarPluginLoader requires sha256
 * and verifies the bytes before loading.
 */
@Serializable
data class PluginArtifact(val uri: String, val sha256: String? = null) {
    init {
        require(uri.isNotBlank())
        require(sha256 == null || sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "sha256 must be a 64-character hexadecimal digest"
        }
    }
}

@Serializable
data class PluginDependency(
    val id: PluginId,
    val exactVersion: PluginVersion
)

@Serializable
data class PluginDescriptor(
    val id: PluginId,
    val version: PluginVersion,
    val hostApi: Int,
    val requiredCapabilities: Set<String> = emptySet(),
    val dependencies: List<PluginDependency> = emptyList(),
    val artifacts: Map<PluginTarget, PluginArtifact>
) {
    init {
        require(hostApi > 0)
    }
}

@Serializable
data class PluginManifest(val plugins: List<PluginDescriptor>)

data class PluginHostContext(
    val hostApi: Int,
    val capabilities: Set<String>,
    val properties: Map<String, String> = emptyMap()
) {
    init {
        require(hostApi > 0)
    }
}

sealed interface PluginFailure {
    data class DuplicateId(val id: PluginId) : PluginFailure
    data class MissingPlugin(val id: PluginId) : PluginFailure
    data class DependencyCycle(val path: List<PluginId>) : PluginFailure
    data class VersionMismatch(
        val id: PluginId,
        val required: PluginVersion,
        val actual: PluginVersion
    ) : PluginFailure
    data class HostApiMismatch(val id: PluginId, val required: Int, val actual: Int) : PluginFailure
    data class MissingCapabilities(val id: PluginId, val missing: Set<String>) : PluginFailure
    data class MissingArtifact(val id: PluginId, val target: PluginTarget) : PluginFailure
    data class CatalogFailed(val message: String) : PluginFailure
    data class SlotSelectionMismatch(val current: Set<PluginId>, val requested: Set<PluginId>) : PluginFailure
    data class LoadingFailed(val id: PluginId, val message: String) : PluginFailure
    data class ActivationFailed(val id: PluginId, val message: String) : PluginFailure
    data class CleanupFailed(val id: PluginId, val message: String) : PluginFailure
}

/** Checked computations are values of the already implemented sum type. */
typealias PluginResult<T> = Sum2<PluginFailure, T>

/**
 * A pure, deterministic dependency plan: dependencies always appear before
 * dependents. Plans contain no executable code, class loaders or JS imports.
 */
data class PluginPlan internal constructor(val ordered: List<PluginDescriptor>)

/** Pure planning; exact-version dependencies are intentionally explicit. */
@Suppress("FunctionName")
fun Plan(
    manifest: PluginManifest,
    selected: Set<PluginId>,
    target: PluginTarget,
    host: PluginHostContext
): PluginResult<PluginPlan> {
    val byId = mutableMapOf<PluginId, PluginDescriptor>()
    for (entry in manifest.plugins) {
        if (byId.put(entry.id, entry) != null) {
            return Sum2.Left(PluginFailure.DuplicateId(entry.id))
        }
    }

    val visited = mutableSetOf<PluginId>()
    val visiting = mutableListOf<PluginId>()
    val ordered = mutableListOf<PluginDescriptor>()

    fun visit(id: PluginId): PluginFailure? {
        if (id in visited) return null
        val cycleIndex = visiting.indexOf(id)
        if (cycleIndex >= 0) {
            return PluginFailure.DependencyCycle(visiting.drop(cycleIndex) + id)
        }
        val plugin = byId[id] ?: return PluginFailure.MissingPlugin(id)
        if (plugin.hostApi != host.hostApi) {
            return PluginFailure.HostApiMismatch(id, plugin.hostApi, host.hostApi)
        }
        val missing = plugin.requiredCapabilities - host.capabilities
        if (missing.isNotEmpty()) {
            return PluginFailure.MissingCapabilities(id, missing)
        }
        if (target !in plugin.artifacts) {
            return PluginFailure.MissingArtifact(id, target)
        }

        visiting += id
        for (requirement in plugin.dependencies.sortedBy { it.id.value }) {
            val dependency = byId[requirement.id]
                ?: return PluginFailure.MissingPlugin(requirement.id)
            if (dependency.version != requirement.exactVersion) {
                return PluginFailure.VersionMismatch(
                    requirement.id, requirement.exactVersion, dependency.version
                )
            }
            visit(requirement.id)?.let { return it }
        }
        visiting.removeAt(visiting.lastIndex)
        visited += id
        ordered += plugin
        return null
    }

    for (id in selected.sortedBy { it.value }) {
        visit(id)?.let { return Sum2.Left(it) }
    }
    return Sum2.Right(PluginPlan(ordered))
}
