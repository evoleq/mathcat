package org.evoleq.mathcat.plugins

import org.evoleq.mathcat.core.optics.Prism
import org.evoleq.mathcat.types.Sum2

/**
 * One explicitly owned plugin selection, either absent or activated.
 * The host retains the returned slot and calls its deployment.stop() on
 * application shutdown. This is not a concurrent or multi-tenant registry.
 */
sealed interface PluginSlot {
    data object Missing : PluginSlot
    data class Loaded(
        val selection: Set<PluginId>,
        val deployment: PluginDeployment
    ) : PluginSlot
}

/** Lawful sum-case optic: match and build do not load modules. */
val LoadedPlugin: Prism<PluginSlot, PluginSlot.Loaded> = Prism(
    match = { state ->
        when (state) {
            is PluginSlot.Loaded -> Sum2.Left(state)
            PluginSlot.Missing -> Sum2.Right(state)
        }
    },
    build = { loaded -> loaded }
)

/** A pure description of the result of matching one plugin slot. */
sealed interface PluginLookup {
    data class Present(val loaded: PluginSlot.Loaded) : PluginLookup
    data object Missing : PluginLookup
}

/** Make matching an explicit stage; its only work is Prism.match(). */
@Suppress("FunctionName")
fun MatchPlugin(): PluginStep<PluginSlot, PluginLookup> = PluginStep { state ->
    when (val found = LoadedPlugin.match(state)) {
        is Sum2.Left -> Sum2.Right(PluginLookup.Present(found.value))
        is Sum2.Right -> Sum2.Right(PluginLookup.Missing)
    }
}

/**
 * Only when the Prism misses, fetch a fresh manifest and load/activate code.
 * Build is deliberately PURE: it inserts an already activated deployment
 * into the tagged sum. Dynamic loading remains an effectful Kleisli arrow.
 *
 * A slot represents ONE selected set. Reusing it for a different set
 * returns an explicit error rather than leaking or silently replacing code.
 */
@Suppress("FunctionName")
fun LoadIfMissing(
    selection: Set<PluginId>,
    catalog: PluginCatalog,
    loader: PluginLoader,
    target: PluginTarget,
    host: PluginHostContext
): PluginStep<PluginLookup, PluginSlot> = PluginStep { lookup ->
    when (lookup) {
        is PluginLookup.Present -> {
            if (lookup.loaded.selection == selection) Sum2.Right(lookup.loaded)
            else Sum2.Left(
                PluginFailure.SlotSelectionMismatch(lookup.loaded.selection, selection)
            )
        }
        PluginLookup.Missing -> {
            val activation = Discover(catalog, target, host) * Activate(loader, target, host)
            when (val result = activation.run(selection)) {
                is Sum2.Left -> result
                is Sum2.Right -> Sum2.Right(
                    LoadedPlugin.build(PluginSlot.Loaded(selection, result.value))
                )
            }
        }
    }
}

/**
 * Typed composition: MatchPlugin() * LoadIfMissing(...).
 * The slot is a value; the host is responsible for keeping the new value.
 */
@Suppress("FunctionName")
fun AccessPlugin(
    selection: Set<PluginId>,
    catalog: PluginCatalog,
    loader: PluginLoader,
    target: PluginTarget,
    host: PluginHostContext
): PluginStep<PluginSlot, PluginSlot> =
    MatchPlugin() * LoadIfMissing(selection, catalog, loader, target, host)
