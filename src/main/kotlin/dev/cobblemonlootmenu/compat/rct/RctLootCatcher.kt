package dev.cobblemonlootmenu.compat.rct

import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.config.LootMenuLog
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.UUID

object RctLootCatcher {
    private data class Watch(
        val player: ServerPlayer,
        val level: ServerLevel,
        val position: Vec3,
        val trainerName: Component,
        val expiresAtTick: Int,
        val collected: MutableList<ItemStack> = mutableListOf()
    )

    // grabs rct drops right after trainer wins
    private val activeWatches = mutableMapOf<UUID, Watch>()

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            CompatDiagnostics.rct.victoriesObserved++
            val battle = event.battle

            val trainerActor = battle.actors.firstOrNull { actor ->
                actor is EntityBackedBattleActor<*> && actor.entity is TrainerMob
            } as? EntityBackedBattleActor<*> ?: return@subscribe

            val trainerMob = trainerActor.entity as? TrainerMob ?: return@subscribe
            val level = trainerMob.level() as? ServerLevel ?: return@subscribe

            // coop winner lookup needs more work
            val player = battle.players.firstOrNull() ?: return@subscribe

            CompatDiagnostics.rct.trainerVictoriesObserved++
            CompatDiagnostics.rct.lastTrainerName = trainerMob.name.string

            LootMenuLog.diagnostic(
                "[rct] victory vs {} ({}), watching nearby drops",
                trainerMob.name.string,
                trainerMob.uuid
            )

            activeWatches[trainerMob.uuid] = Watch(
                player = player,
                level = level,
                position = trainerMob.position(),
                trainerName = trainerMob.name,
                expiresAtTick = level.server.tickCount + LootMenuConfig.values.rctWatchTicks
            )
            CompatDiagnostics.rct.activeWatches = activeWatches.size
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, level ->
            if (entity !is ItemEntity || activeWatches.isEmpty()) return@register
            val serverLevel = level as? ServerLevel ?: return@register
            val radiusSq = LootMenuConfig.values.rctCaptureRadius *
                LootMenuConfig.values.rctCaptureRadius

            val match = activeWatches.values.firstOrNull { watch ->
                watch.level == serverLevel &&
                    entity.position().distanceToSqr(watch.position) <= radiusSq
            } ?: return@register

            val stack = entity.item.copy()
            match.collected.add(stack)
            entity.discard()

            CompatDiagnostics.rct.capturedStacks++
            CompatDiagnostics.rct.lastCaptureSize = stack.count
            LootMenuLog.diagnostic(
                "[rct] caught {} x{}",
                stack.hoverName.string,
                stack.count
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (activeWatches.isEmpty()) return@register
            val currentTick = server.tickCount
            val finishedKeys = activeWatches
                .filterValues { it.expiresAtTick <= currentTick }
                .keys
                .toList()

            finishedKeys.forEach { key ->
                val watch = activeWatches.remove(key) ?: return@forEach
                CompatDiagnostics.rct.activeWatches = activeWatches.size

                if (watch.collected.isEmpty()) {
                    CompatDiagnostics.rct.emptyWatches++
                    LootMenuLog.diagnostic("[rct] no loot found for watch {}", key)
                    return@forEach
                }

                CobblemonLootMenuApi.enqueue(
                    LootMenuRequest(
                        player = watch.player,
                        level = watch.level,
                        dropPosition = watch.position,
                        title = Component.translatable(
                            "screen.cobblemon_loot_menu.title",
                            watch.trainerName
                        ),
                        stacks = watch.collected,
                        sourceId = LootSources.RCT_TRAINER
                    )
                )
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            activeWatches.clear()
            CompatDiagnostics.rct.activeWatches = 0
        }
    }
}
