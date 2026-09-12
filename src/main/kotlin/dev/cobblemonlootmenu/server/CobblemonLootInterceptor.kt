package dev.cobblemonlootmenu.server

import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.serialization.JsonOps
import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.compat.wildbosses.WildBossesLootBridge
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.config.LootMenuLog
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.RegistryOps
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

object CobblemonLootInterceptor {
    fun register() {
        CobblemonEvents.LOOT_DROPPED.subscribe { event ->
            if (!LootMenuConfig.values.enableWildPokemonLoot) return@subscribe

            LootMenuLog.diagnostic(
                "[cobblemon] loot event: entity={}, player={}, drops={}",
                event.entity?.javaClass?.simpleName ?: "null",
                event.player?.gameProfile?.name ?: "null",
                event.drops.size
            )

            val pokemonEntity = event.entity as? PokemonEntity
            if (pokemonEntity == null) {
                LootMenuLog.diagnostic("[cobblemon] skipped: source isn't a pokemon entity")
                return@subscribe
            }

            val isAlpha = pokemonEntity.pokemon.isAlpha
            val player = event.player ?: if (isAlpha) {
                AlphaLootBridge.playerFor(pokemonEntity.uuid)
            } else {
                null
            }
            if (player == null) {
                LootMenuLog.diagnostic("[cobblemon] skipped: no player owner")
                return@subscribe
            }

            if (!pokemonEntity.pokemon.isWild()) {
                LootMenuLog.diagnostic("[cobblemon] skipped: pokemon isn't wild")
                return@subscribe
            }

            val level = pokemonEntity.level() as? ServerLevel
            if (level == null) {
                LootMenuLog.diagnostic("[cobblemon] skipped: source level isn't server-side")
                return@subscribe
            }

            val itemEntries = event.drops.filterIsInstance<ItemDropEntry>()
            if (itemEntries.isEmpty() && !isAlpha) {
                LootMenuLog.diagnostic("[cobblemon] skipped: no item drop entries")
                return@subscribe
            }

            val materializedDrops = itemEntries.map { entry -> materialize(entry, level) }
            if (materializedDrops.any { it is MaterializedDrop.Failed }) {
                LootMenuLog.diagnostic("[cobblemon] skipped: failed to materialize item entries")
                return@subscribe
            }

            val rolledStacks = materializedDrops.mapNotNull { result ->
                (result as? MaterializedDrop.Stack)?.stack
            }

            val dropPosition = pokemonEntity.position()
            val speciesName = pokemonEntity.pokemon.getDisplayName(false)

            event.cancel()

            event.drops
                .filterNot { it is ItemDropEntry }
                .forEach { entry ->
                    entry.drop(pokemonEntity, level, dropPosition, player)
                }

            if (isAlpha) {
                AlphaLootBridge.stageSpeciesLoot(
                    entity = pokemonEntity,
                    player = player,
                    stacks = rolledStacks
                )
                return@subscribe
            }

            if (rolledStacks.isEmpty()) {
                LootMenuLog.diagnostic("[cobblemon] item entries rolled no stacks")
                return@subscribe
            }

            val bossCompatReady = CompatDiagnostics.wildBossesIntegration.registered
            val isBoss = bossCompatReady && WildBossesLootBridge.isBoss(pokemonEntity.uuid)

            if (isBoss) {
                // boss loot gets merged into one screen
                WildBossesLootBridge.stashSpeciesLoot(
                    entityUuid = pokemonEntity.uuid,
                    player = player,
                    level = level,
                    dropPosition = dropPosition,
                    speciesName = speciesName,
                    stacks = rolledStacks
                )
                return@subscribe
            }

            CobblemonLootMenuApi.enqueue(
                LootMenuRequest(
                    player = player,
                    level = level,
                    dropPosition = dropPosition,
                    title = Component.translatable(
                        "screen.cobblemon_loot_menu.title",
                        speciesName
                    ),
                    stacks = rolledStacks,
                    sourceId = LootSources.WILD_POKEMON
                )
            )
        }

        CobblemonLootMenuConstants.LOGGER.info("Cobblemon loot listener registered")
    }

    private sealed interface MaterializedDrop {
        data class Stack(val stack: ItemStack) : MaterializedDrop
        data object Empty : MaterializedDrop
        data object Failed : MaterializedDrop
    }

    private fun materialize(entry: ItemDropEntry, level: ServerLevel): MaterializedDrop {
        val item = level.registryAccess()
            .registryOrThrow(Registries.ITEM)
            .get(entry.item)
            ?: run {
                CobblemonLootMenuConstants.LOGGER.warn(
                    "Unable to materialize Cobblemon drop {}",
                    entry.item
                )
                return MaterializedDrop.Failed
            }

        val count = entry.quantityRange?.random() ?: entry.quantity
        if (count <= 0) return MaterializedDrop.Empty

        return runCatching {
            val stack = ItemStack(item, count)
            val components = entry.components

            if (components != null) {
                val registryOps = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess())
                val patch = DataComponentPatch.CODEC
                    .parse(registryOps, components)
                    .result()
                    .orElse(null)
                    ?: return MaterializedDrop.Failed

                stack.applyComponentsAndValidate(patch)
            }

            MaterializedDrop.Stack(stack)
        }.getOrElse { error ->
            CobblemonLootMenuConstants.LOGGER.warn(
                "Unable to apply Cobblemon drop components for {}",
                entry.item,
                error
            )
            MaterializedDrop.Failed
        }
    }
}
