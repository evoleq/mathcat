package org.evoleq.mathcat.plugins.actions

import kotlinx.coroutines.test.runTest
import org.evoleq.mathcat.actions.Action
import org.evoleq.mathcat.actions.ActionDispatcher
import org.evoleq.mathcat.actions.ActionEnvelope
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.writer.Writer
import org.evoleq.mathcat.plugins.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActionNavigationBridgeTest {
    private data class App(val count: Int)
    private val refresh = Action(
        name = "refresh-shares",
        reader = Reader<App, Unit> { Unit },
        writer = Writer<App, Unit> { _ -> { current -> current } }
    )

    @Test
    fun pluginCanOnlyInvokeExplicitlyGrantedTypedActionAndInternalNavigation() = runTest {
        val received = mutableListOf<ActionEnvelope<App, *, *>>()
        val paths = mutableListOf<String>()
        val dispatcher: ActionDispatcher<App> = ActionDispatcher { received += it }
        val bridge = ActionNavigationBridge(
            dispatcher = dispatcher,
            navigator = PluginNavigator { paths += it },
            actions = listOf(BindAction("shares.refresh", refresh)),
            routes = listOf(PluginRoute("shares.home", "/app/shares")),
            grants = mapOf(
                PluginId("shares") to PluginGrant(
                    actions = setOf("shares.refresh"),
                    routes = setOf("shares.home")
                )
            )
        )
        assertTrue(bridge.dispatchAction(PluginId("shares"), "shares.refresh"))
        assertTrue(bridge.navigate(PluginId("shares"), "shares.home"))
        assertEquals(listOf("refresh-shares"), received.map { it.action.name })
        assertEquals(listOf("/app/shares"), paths)

        assertFalse(bridge.dispatchAction(PluginId("banking"), "shares.refresh"))
        assertFalse(bridge.navigate(PluginId("banking"), "shares.home"))
        assertFalse(bridge.dispatchAction(PluginId("shares"), "admin.delete"))
        assertFalse(bridge.navigate(PluginId("shares"), "https://example.com"))
        assertEquals(1, received.size)
        assertEquals(1, paths.size)
    }

    @Test
    fun envelopeMetadataRemainsIntactAndHostOwnsTheEnvelope() = runTest {
        val received = mutableListOf<ActionEnvelope<App, *, *>>()
        val envelope = ActionEnvelope(
            action = refresh,
            id = "shares-refresh",
            meta = mapOf("source" to "host")
        )
        val bridge = ActionNavigationBridge(
            dispatcher = ActionDispatcher<App> { received += it },
            navigator = PluginNavigator { },
            actions = listOf(BindEnvelope("shares.refresh", envelope)),
            routes = emptyList(),
            grants = mapOf(PluginId("shares") to PluginGrant(actions = setOf("shares.refresh")))
        )
        assertTrue(bridge.dispatchAction(PluginId("shares"), "shares.refresh"))
        assertEquals(listOf("shares-refresh"), received.map { it.id })
        assertEquals(mapOf("source" to "host"), received.single().meta)
    }

    @Test
    fun invalidRouteAndDuplicateBindingsAreRejectedUpFront() {
        assertFailsWith<IllegalArgumentException> { PluginRoute("bad", "https://example.com") }
        assertFailsWith<IllegalArgumentException> { PluginRoute("bad", "//remote.example") }
        assertFailsWith<IllegalArgumentException> {
            ActionNavigationBridge(
                dispatcher = ActionDispatcher<App> { },
                navigator = PluginNavigator {},
                actions = listOf(
                    BindAction("dup", refresh),
                    BindAction("dup", refresh)
                ),
                routes = emptyList(),
                grants = emptyMap()
            )
        }
    }
}
