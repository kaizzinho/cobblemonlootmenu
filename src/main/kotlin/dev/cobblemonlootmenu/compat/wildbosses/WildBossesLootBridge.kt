package dev.cobblemonlootmenu.compat.wildbosses

import com.kaizzinho.wildbosses.api.WildBossLootEvents
import com.kaizzinho.wildbosses.boss.BossRegistry
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.config.LootMenuLog
import dev.cobblemonlootmenu.server.AlphaLootBridge
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
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

    private data class PendingBossAward(
        val stacks: List<ItemStack>,
        val player: ServerPlayer,
        val level: ServerLevel,
        val position: Vec3,
        val bossName: String,
        val expiresAtTick: Long
    )

    private val speciesLootStash = mutableMapOf<UUID, SpeciesLootStash>()
    private val pendingBossAwards = mutableMapOf<UUID, PendingBossAward>()

    fun register() {
        WildBossLootEvents.subscribe { event ->
            CompatDiagnostics.wildBosses.awardsObserved++
            val player = event.player ?: return@subscribe
            val level = event.level as? ServerLevel ?: return@subscribe
            val bossName = "${event.tierName.replaceFirstChar { it.uppercase() }} Boss ${event.speciesName}"

            CompatDiagnostics.wildBosses.lastBossName = bossName

            if (AlphaLootBridge.isWatching(event.entityUuid) && event.stacks.isNotEmpty()) {
                event.claim()
                pendingBossAwards[event.entityUuid] = PendingBossAward(
                    stacks = event.stacks.filterNot { it.isEmpty }.map(ItemStack::copy),
                    player = player,
                    level = level,
                    position = event.position,
                    bossName = bossName,
                    expiresAtTick = level.server.tickCount + ALPHA_AWARD_WAIT_TICKS
                )
                updateActiveStashes()
                LootMenuLog.diagnostic(
                    "[wildbosses] delayed alpha award {} for merge",
                    event.entityUuid
                )
                return@subscribe
            }

            val stashed = speciesLootStash.remove(event.entityUuid)
            val combinedStacks = event.stacks + (stashed?.stacks ?: emptyList())
            if (combinedStacks.isEmpty()) return@subscribe

            updateActiveStashes()
            if (stashed != null) CompatDiagnostics.wildBosses.mergedAwards++

            LootMenuLog.diagnostic(
                "[wildbosses] award {}, stacks={}, merged={}",
                event.entityUuid,
                combinedStacks.size,
                stashed != null
            )

            event.claim()
            enqueueBossLoot(
                player = player,
                level = level,
                position = event.position,
                bossName = bossName,
                stacks = combinedStacks
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            val currentTick = server.tickCount

            if (pendingBossAwards.isNotEmpty()) {
                val readyKeys = pendingBossAwards
                    .filter { (entityUuid, award) ->
                        speciesLootStash.containsKey(entityUuid) || award.expiresAtTick <= currentTick
                    }
                    .keys
                    .toList()

                readyKeys.forEach { entityUuid ->
                    val award = pendingBossAwards.remove(entityUuid) ?: return@forEach
                    val stashed = speciesLootStash.remove(entityUuid)
                    val combinedStacks = award.stacks + (stashed?.stacks ?: emptyList())

                    if (stashed != null) CompatDiagnostics.wildBosses.mergedAwards++
                    updateActiveStashes()

                    LootMenuLog.diagnostic(
                        "[wildbosses] alpha award {}, stacks={}, merged={}",
                        entityUuid,
                        combinedStacks.size,
                        stashed != null
                    )

                    if (combinedStacks.isNotEmpty()) {
                        enqueueBossLoot(
                            player = award.player,
                            level = award.level,
                            position = award.position,
                            bossName = award.bossName,
                            stacks = combinedStacks
                        )
                    }
                }
            }

            if (speciesLootStash.isEmpty()) return@register
            val expiredKeys = speciesLootStash
                .filterValues { it.expiresAtTick <= currentTick }
                .keys
                .toList()

            expiredKeys.forEach { key ->
                val stash = speciesLootStash.remove(key) ?: return@forEach
                updateActiveStashes()

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
            speciesLootStash.values.forEach { stash ->
                dropStacks(stash.level, stash.position, stash.stacks)
            }
            pendingBossAwards.values.forEach { award ->
                dropStacks(award.level, award.position, award.stacks)
            }
            speciesLootStash.clear()
            pendingBossAwards.clear()
            updateActiveStashes()
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
        updateActiveStashes()
        LootMenuLog.diagnostic("[wildbosses] stashed species loot for {}", entityUuid)
    }

    private fun enqueueBossLoot(
        player: ServerPlayer,
        level: ServerLevel,
        position: Vec3,
        bossName: String,
        stacks: List<ItemStack>
    ) {
        CobblemonLootMenuApi.enqueue(
            LootMenuRequest(
                player = player,
                level = level,
                dropPosition = position,
                title = Component.translatable(
                    "screen.cobblemon_loot_menu.title",
                    Component.literal(bossName)
                ),
                stacks = stacks,
                sourceId = LootSources.WILD_BOSS
            )
        )
    }

    private fun updateActiveStashes() {
        CompatDiagnostics.wildBosses.activeStashes =
            speciesLootStash.size + pendingBossAwards.size
    }

    private fun dropStacks(level: ServerLevel, position: Vec3, stacks: List<ItemStack>) {
        stacks.forEach { stack ->
            if (stack.isEmpty) return@forEach
            level.addFreshEntity(
                ItemEntity(
                    level,
                    position.x,
                    position.y,
                    position.z,
                    stack.copy()
                )
            )
        }
    }

    private const val ALPHA_AWARD_WAIT_TICKS = 20L
}
