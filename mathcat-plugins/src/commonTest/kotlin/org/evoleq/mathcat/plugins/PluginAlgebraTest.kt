package org.evoleq.mathcat.plugins

import kotlinx.coroutines.test.runTest
import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginAlgebraTest {
    private val host = PluginHostContext(
        hostApi = 1,
        capabilities = setOf("navigation", "actions")
    )
    private val one = PluginVersion(1, 0, 0)
    private fun plugin(
        name: String,
        deps: List<PluginDependency> = emptyList(),
        caps: Set<String> = emptySet(),
        api: Int = 1
    ) = PluginDescriptor(
        id = PluginId(name),
        version = one,
        hostApi = api,
        requiredCapabilities = caps,
        dependencies = deps,
        artifacts = mapOf(PluginTarget.JS to PluginArtifact("/$name.3ab241c9.js"))
    )

    private val base = plugin("base")
    private val feature = plugin(
        "feature",
        deps = listOf(PluginDependency(base.id, one)),
        caps = setOf("actions")
    )

    @Test
    fun manifestJsonRoundTripsAcrossAllKmpTargets() {
        val original = PluginManifest(listOf(feature, base))
        assertEquals(original, ParseManifest(original.toJson()))
    }

    @Test
    fun discoverUsesFreshManifestAndComposesWithKleisliResolution() = runTest {
        var catalog = PluginManifest(listOf(base))
        val live = PluginCatalog { catalog }
        val discovery = Discover(live, PluginTarget.JS, host)
        assertEquals(listOf(base.id),
            assertIs<Sum2.Right<PluginPlan>>(discovery.run(setOf(base.id))).value.ordered.map { it.id }
        )
        catalog = PluginManifest(listOf(feature, base))
        assertEquals(listOf(base.id, feature.id),
            assertIs<Sum2.Right<PluginPlan>>(discovery.run(setOf(feature.id))).value.ordered.map { it.id }
        )
    }

    @Test
    fun dependencyPlanningIsPureStableAndPlacesDependenciesBeforeDependents() {
        val catalog = PluginManifest(listOf(feature, base))
        val result = Plan(catalog, setOf(feature.id), PluginTarget.JS, host)
        val plan = assertIs<Sum2.Right<PluginPlan>>(result).value
        assertEquals(listOf(base.id, feature.id), plan.ordered.map { it.id })
        assertEquals(
            plan,
            assertIs<Sum2.Right<PluginPlan>>(
                Plan(catalog, setOf(feature.id), PluginTarget.JS, host)
            ).value
        )
    }

    @Test
    fun missingDependenciesAndCyclesAreExplicitValues() {
        val missing = Plan(
            PluginManifest(listOf(feature)), setOf(feature.id), PluginTarget.JS, host
        )
        assertEquals(
            PluginFailure.MissingPlugin(base.id),
            assertIs<Sum2.Left<PluginFailure>>(missing).value
        )
        val a = plugin("a", listOf(PluginDependency(PluginId("b"), one)))
        val b = plugin("b", listOf(PluginDependency(a.id, one)))
        val cycle = Plan(PluginManifest(listOf(a, b)), setOf(a.id), PluginTarget.JS, host)
        assertEquals(
            PluginFailure.DependencyCycle(listOf(a.id, b.id, a.id)),
            assertIs<Sum2.Left<PluginFailure>>(cycle).value
        )
    }

    @Test
    fun compatibilityCatchesMissingCapabilitiesHostApiAndVersionSkew() {
        assertEquals(
            PluginFailure.MissingCapabilities(PluginId("needsCamera"), setOf("camera")),
            assertIs<Sum2.Left<PluginFailure>>(
                Plan(
                    PluginManifest(listOf(plugin("needsCamera", caps = setOf("camera")))),
                    setOf(PluginId("needsCamera")), PluginTarget.JS, host
                )
            ).value
        )
        assertEquals(
            PluginFailure.HostApiMismatch(PluginId("v2"), 2, 1),
            assertIs<Sum2.Left<PluginFailure>>(
                Plan(PluginManifest(listOf(plugin("v2", api = 2))),
                    setOf(PluginId("v2")), PluginTarget.JS, host)
            ).value
        )
        val needsOtherVersion = plugin(
            "consumer",
            deps = listOf(PluginDependency(base.id, PluginVersion(2, 0, 0)))
        )
        assertEquals(
            PluginFailure.VersionMismatch(base.id, PluginVersion(2, 0, 0), one),
            assertIs<Sum2.Left<PluginFailure>>(
                Plan(PluginManifest(listOf(base, needsOtherVersion)),
                    setOf(needsOtherVersion.id), PluginTarget.JS, host)
            ).value
        )
    }

    @Test
    fun kleisliCompositionShortCircuitsFailuresAndPreservesValues() = runTest {
        var executed = 0
        val first = PluginStep<Int, Int> { Sum2.Right(it + 1) }
        val second = PluginStep<Int, Int> { executed++; Sum2.Right(it * 2) }
        val error = PluginFailure.MissingPlugin(PluginId("missing"))
        val fail = PluginStep<Int, Int> { Sum2.Left(error) }

        assertEquals(Sum2.Right(10), (first * second).run(4))
        assertEquals(1, executed)
        assertEquals(Sum2.Left(error), (fail * second).run(4))
        assertEquals(1, executed)
    }

    @Test
    fun resolveAndActivateComposeAndDisposeInReverseOrder() = runTest {
        val events = mutableListOf<String>()
        val loader = PluginLoader { plugin, _ ->
            events += "load:${plugin.id.value}"
            object : PluginHandle {
                override suspend fun activate(host: PluginHostContext): PluginSession {
                    events += "activate:${plugin.id.value}"
                    return PluginSession { events += "dispose:${plugin.id.value}" }
                }
                override suspend fun unload() {
                    events += "unload:${plugin.id.value}"
                }
            }
        }
        val pipeline = Resolve(PluginManifest(listOf(feature, base)), PluginTarget.JS, host) *
            Activate(loader, PluginTarget.JS, host)
        val outcome = pipeline.run(setOf(feature.id))
        val deployment = assertIs<Sum2.Right<PluginDeployment>>(outcome).value
        assertEquals(listOf(base.id, feature.id), deployment.active)
        assertEquals(Sum2.Right(Unit), deployment.stop())
        assertEquals(Sum2.Right(Unit), deployment.stop())
        assertEquals(listOf(
            "load:base", "activate:base", "load:feature", "activate:feature",
            "dispose:feature", "dispose:base", "unload:feature", "unload:base"
        ), events)
    }

    @Test
    fun failedActivationRollsBackSuccessfullyStartedDependencies() = runTest {
        val events = mutableListOf<String>()
        val loader = PluginLoader { plugin, _ ->
            events += "load:${plugin.id.value}"
            object : PluginHandle {
                override suspend fun activate(host: PluginHostContext): PluginSession {
                    if (plugin.id == feature.id) throw IllegalStateException("feature failed")
                    events += "activate:${plugin.id.value}"
                    return PluginSession { events += "dispose:${plugin.id.value}" }
                }
                override suspend fun unload() {
                    events += "unload:${plugin.id.value}"
                }
            }
        }
        val plan = assertIs<Sum2.Right<PluginPlan>>(
            Plan(PluginManifest(listOf(feature, base)),
                setOf(feature.id), PluginTarget.JS, host)
        ).value
        val outcome = Activate(loader, PluginTarget.JS, host).run(plan)
        assertEquals(
            PluginFailure.ActivationFailed(feature.id, "feature failed"),
            assertIs<Sum2.Left<PluginFailure>>(outcome).value
        )
        assertEquals(listOf(
            "load:base", "activate:base", "load:feature",
            "dispose:base", "unload:feature", "unload:base"
        ), events)
    }
}
