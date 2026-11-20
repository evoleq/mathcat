package org.evoleq.mathcat.routing

import org.evoleq.mathcat.evolution.Evolution
import org.evoleq.mathcat.types.Sum2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * This suite is executable documentation for the pure router contract.
 * None of these tests starts a browser, coroutine or plugin.
 */
class RouterTest {
    private fun compiled(configure: RoutesConfiguration.() -> Unit): CompiledRoutes =
        assertIs<Sum2.Right<CompiledRoutes>>(CompileRoutes(routes(configure))).value

    private fun shares(): CompiledRoutes = compiled {
        route("app") {
            // Declaration order is irrelevant to inherited route metadata.
            route("organisationen/:organisation/anteile") {
                name("shares.detail")
            }
            requires("logged-in", "shares.read")
            plugin("shares")
            layout("shell")
        }
    }

    @Test fun documentedNavigationSequenceChecksAccessThenLoadsPluginThenCommitsHistory() {
        val router = Router(shares())
        val url = "/app/organisationen/sonnenhof/anteile?tab=history"
        val beginning = router.evolve(RouterState(), RouterEvent.Navigate(url))
        val checking = assertIs<RouterPhase.Checking>(beginning.state.phase)
        assertEquals(1L, checking.generation)
        assertEquals("sonnenhof", checking.navigation.match.parameters["organisation"])
        assertEquals(listOf("history"), checking.navigation.match.query["tab"])
        assertEquals(setOf("logged-in", "shares.read"), checking.navigation.match.requires)
        assertEquals("shares", checking.navigation.match.plugin)
        assertEquals(listOf("shell"), checking.navigation.match.layouts)
        assertEquals(
            listOf(RouterCommand.CheckAccess(1, checking.navigation.match)),
            beginning.commands
        )

        val authorized = router.evolve(beginning.state, RouterEvent.AccessResolved(1, true))
        assertIs<RouterPhase.Loading>(authorized.state.phase)
        assertEquals(listOf(RouterCommand.LoadPlugin(1, "shares")), authorized.commands)

        val activated = router.evolve(authorized.state, RouterEvent.PluginActivated(1, "shares"))
        assertIs<RouterPhase.Active>(activated.state.phase)
        assertEquals(listOf(RouterCommand.ChangeHistory(url, HistoryMode.Push)), activated.commands)
    }

    @Test fun browserDeepLinkNeverPushesItselfAgain() {
        val router = Router(compiled { route("manual/:topic") { name("topic") } })
        val outcome = router.evolve(
            RouterState(), RouterEvent.Navigate("/manual/Fr%C3%BChjahr", HistoryMode.None)
        )
        val active = assertIs<RouterPhase.Active>(outcome.state.phase)
        assertEquals("Frühjahr", active.navigation.match.parameters["topic"])
        assertTrue(outcome.commands.isEmpty())
    }

    @Test fun replaceNavigationEmitsReplaceOnlyAfterActivation() {
        val router = Router(compiled {
            route("app/dashboard") { name("dashboard") }
        })
        val result = router.evolve(RouterState(), RouterEvent.Navigate(
            "/app/dashboard", HistoryMode.Replace
        ))
        assertEquals(
            listOf(RouterCommand.ChangeHistory("/app/dashboard", HistoryMode.Replace)),
            result.commands
        )
        assertIs<RouterPhase.Active>(result.state.phase)
    }

    @Test fun deniedRouteNeverRequestsPluginOrChangesHistory() {
        val router = Router(shares())
        val matching = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/a/anteile"))
        val denied = router.evolve(matching.state, RouterEvent.AccessResolved(1, false))
        assertIs<RouterPhase.Denied>(denied.state.phase)
        assertTrue(denied.commands.isEmpty())
        val impossible = router.evolve(denied.state, RouterEvent.PluginActivated(1, "shares"))
        assertEquals(denied.state, impossible.state)
        assertTrue(impossible.commands.isEmpty())
    }

    @Test fun staleAuthorizationCannotActivateTheNewLocation() {
        val router = Router(shares())
        val first = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/old/anteile"))
        val second = router.evolve(first.state, RouterEvent.Navigate("/app/organisationen/new/anteile"))
        assertEquals(listOf(
            RouterCommand.CancelPending(1),
            RouterCommand.CheckAccess(2, assertIs<RouterPhase.Checking>(second.state.phase).navigation.match)
        ), second.commands)
        val stale = router.evolve(second.state, RouterEvent.AccessResolved(1, true))
        assertEquals(second.state, stale.state)
        assertTrue(stale.commands.isEmpty())
        assertIs<RouterPhase.Loading>(router.evolve(stale.state, RouterEvent.AccessResolved(2, true)).state.phase)
    }

    @Test fun stalePluginResultAndWrongPluginIdCannotOverwriteNewRoute() {
        val router = Router(shares())
        val first = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/old/anteile"))
        val loading = router.evolve(first.state, RouterEvent.AccessResolved(1, true))
        val second = router.evolve(loading.state, RouterEvent.Navigate("/app/organisationen/new/anteile"))
        assertEquals(
            RouterCommand.CancelPending(1),
            second.commands.first()
        )
        assertEquals(second.state, router.evolve(second.state, RouterEvent.PluginActivated(1, "shares")).state)

        val secondLoading = router.evolve(second.state, RouterEvent.AccessResolved(2, true))
        val wrong = router.evolve(secondLoading.state, RouterEvent.PluginActivated(2, "other"))
        assertEquals(secondLoading.state, wrong.state)
        assertTrue(wrong.commands.isEmpty())
        assertIs<RouterPhase.Active>(
            router.evolve(secondLoading.state, RouterEvent.PluginActivated(2, "shares")).state.phase
        )
    }

    @Test fun pluginFailureIsExplicitAndRetryGetsFreshGeneration() {
        val router = Router(compiled {
            route("feature") { name("feature"); plugin("remote-feature") }
        })
        val first = router.evolve(RouterState(), RouterEvent.Navigate("/feature"))
        assertEquals(listOf(RouterCommand.LoadPlugin(1, "remote-feature")), first.commands)
        val failed = router.evolve(first.state, RouterEvent.PluginRejected(1, "remote-feature", "offline"))
        assertEquals("offline", assertIs<RouterPhase.Failed>(failed.state.phase).reason)
        assertTrue(failed.commands.isEmpty())
        val retry = router.evolve(failed.state, RouterEvent.Navigate("/feature"))
        assertEquals(2L, retry.state.generation)
        assertEquals(listOf(RouterCommand.LoadPlugin(2, "remote-feature")), retry.commands)
    }

    @Test fun navigationToMissingRouteRejectsAndCancelsPendingGeneration() {
        val router = Router(shares())
        val pending = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/a/anteile"))
        val missing = router.evolve(pending.state, RouterEvent.Navigate("/nothing"))
        assertEquals(RouteError.NotFound("/nothing"),
            assertIs<RouterPhase.Rejected>(missing.state.phase).error)
        assertEquals(listOf(RouterCommand.CancelPending(1)), missing.commands)
    }

    @Test fun invalidEncodedDeepLinkIsAnExplicitRouteFailure() {
        val router = Router(compiled { route("orgs/:org") { name("org") } })
        val result = router.evolve(RouterState(), RouterEvent.Navigate("/orgs/%ZZ"))
        assertIs<RouteError.InvalidUrl>(assertIs<RouterPhase.Rejected>(result.state.phase).error)
        assertTrue(result.commands.isEmpty())
    }

    @Test fun stopCancelsInFlightWorkAndIsIdempotent() {
        val router = Router(shares())
        val pending = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/a/anteile"))
        val stopped = router.evolve(pending.state, RouterEvent.Stop)
        assertEquals(RouterPhase.Stopped, stopped.state.phase)
        assertEquals(listOf(RouterCommand.CancelPending(1)), stopped.commands)
        assertEquals(Evolution(stopped.state, emptyList()), router.evolve(stopped.state, RouterEvent.Stop))
        assertEquals(Evolution(stopped.state, emptyList()),
            router.evolve(stopped.state, RouterEvent.AccessResolved(1, true)))
        assertEquals(Evolution(stopped.state, emptyList()),
            router.evolve(stopped.state, RouterEvent.Navigate("/app/organisationen/a/anteile")))
    }

    @Test fun stopDoesNotDisposeBorrowedOrActivePlugins() {
        val router = Router(compiled { route("feature") { name("feature"); plugin("shared") } })
        val loading = router.evolve(RouterState(), RouterEvent.Navigate("/feature"))
        val active = router.evolve(loading.state, RouterEvent.PluginActivated(1, "shared"))
        val stop = router.evolve(active.state, RouterEvent.Stop)
        assertEquals(RouterPhase.Stopped, stop.state.phase)
        assertTrue(stop.commands.isEmpty())
    }

    @Test fun eventReplayIsDeterministicAndNoEffectIsExecuted() {
        val router = Router(shares())
        val events: List<RouterEvent> = listOf(
            RouterEvent.Navigate("/app/organisationen/a/anteile"),
            RouterEvent.AccessResolved(1, true),
            RouterEvent.PluginActivated(1, "shares")
        )
        fun replay(): Pair<RouterState, List<RouterCommand>> {
            var state = RouterState()
            val recorded = mutableListOf<RouterCommand>()
            for (event in events) {
                val result = router.evolve(state, event)
                state = result.state
                recorded.addAll(result.commands)
            }
            return state to recorded
        }
        assertEquals(replay(), replay())
        assertIs<RouterPhase.Active>(replay().first.phase)
        assertEquals(3, replay().second.size)
    }

    @Test fun guardMetadataIsNotMisrepresentedAsAuthorization() {
        val router = Router(shares())
        val first = router.evolve(RouterState(), RouterEvent.Navigate("/app/organisationen/a/anteile"))
        assertIs<RouterPhase.Checking>(first.state.phase)
        assertEquals(1, first.commands.size)
        // A host must explicitly return its decision: the route metadata alone
        // never grants access, renders a page, or calls a backend.
    }
}
