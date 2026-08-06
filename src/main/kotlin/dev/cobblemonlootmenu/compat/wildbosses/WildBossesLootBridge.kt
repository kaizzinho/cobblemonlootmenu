package dev.cobblemonlootmenu.compat.wildbosses

import com.kaizzinho.wildbosses.api.WildBossLootEvents
import com.kaizzinho.wildbosses.boss.BossRegistry
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.config.LootMenuLog
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.UUID

object WildBossesLootBridge {
    private data class SpeciesLootStash(
        val stacks: List<ItemStack>,
        val player: ServerPlayer,
        val level: ServerLevel,
        val position: Vec3,
        val speciesName: Component,
        val expiresAtTick: Long
    )

    private val speciesLootStash = mutableMapOf<UUID, SpeciesLootStash>()

    fun register() {
        WildBossLootEvents.subscribe { event ->
            CompatDiagnostics.wildBosses.awardsObserved++
            val player = event.player ?: return@subscribe
            val level = event.level as? ServerLevel ?: return@subscribe

            val stashed = speciesLootStash.remove(event.entityUuid)
            val combinedStacks = event.stacks + (stashed?.stacks ?: emptyList())
            if (combinedStacks.isEmpty()) return@subscribe

            val bossName = "${event.tierName.replaceFirstChar { it.uppercase() }} ${event.speciesName}"
            CompatDiagnostics.wildBosses.lastBossName = bossName
            CompatDiagnostics.wildBosses.activeStashes = speciesLootStash.size
            if (stashed != null) CompatDiagnostics.wildBosses.mergedAwards++

            LootMenuLog.diagnostic(
                "[wildbosses] award {}, stacks={}, merged={}",
                event.entityUuid,
                combinedStacks.size,
                stashed != null
            )

            event.claim()
            CobblemonLootMenuApi.enqueue(
                LootMenuRequest(
                    player = player,
                    level = level,
                    dropPosition = event.position,
                    title = Component.translatable(
                        "screen.cobblemon_loot_menu.title",
                        Component.literal(bossName)
                    ),
                    stacks = combinedStacks,
                    sourceId = LootSources.WILD_BOSS
                )
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (speciesLootStash.isEmpty()) return@register
            val currentTick = server.tickCount
            val expiredKeys = speciesLootStash
                .filterValues { it.expiresAtTick <= currentTick }
                .keys
                .toList()

            expiredKeys.forEach { key ->
                val stash = speciesLootStash.remove(key) ?: return@forEach
                CompatDiagnostics.wildBosses.activeStashes = speciesLootStash.size

                CobblemonLootMenuApi.enqueue(
                    LootMenuRequest(
                        player = stash.player,
                        level = stash.level,
                        dropPosition = stash.position,
                        title = Component.translatable(
                            "screen.cobblemon_loot_menu.title",
                            stash.speciesName
                        ),
                        stacks = stash.stacks,
                        sourceId = LootSources.WILD_BOSS
                    )
                )
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            speciesLootStash.clear()
            CompatDiagnostics.wildBosses.activeStashes = 0
        }
    }

    fun isBoss(entityUuid: UUID): Boolean = BossRegistry.isBoss(entityUuid)

    fun stashSpeciesLoot(
        entityUuid: UUID,
        player: ServerPlayer,
        level: ServerLevel,
        dropPosition: Vec3,
        speciesName: Component,
        stacks: List<ItemStack>
    ) {
        if (stacks.isEmpty()) return

        val timeoutTicks = LootMenuConfig.values.wildBossStashTimeoutSeconds * 20L
        speciesLootStash[entityUuid] = SpeciesLootStash(
            stacks = stacks.map(ItemStack::copy),
            player = player,
            level = level,
            position = dropPosition,
            speciesName = speciesName,
            expiresAtTick = level.server.tickCount + timeoutTicks
        )

        CompatDiagnostics.wildBosses.stashesCreated++
        CompatDiagnostics.wildBosses.activeStashes = speciesLootStash.size
        LootMenuLog.diagnostic("[wildbosses] stashed species loot for {}", entityUuid)
    }
}
