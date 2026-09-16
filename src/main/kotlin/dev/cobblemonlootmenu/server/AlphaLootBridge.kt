package dev.cobblemonlootmenu.server

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.compat.wildbosses.WildBossesLootBridge
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

object AlphaLootBridge {
    private data class Watch(
        val level: ServerLevel,
        var position: Vec3,
        val pokemonName: Component,
        var expiresAtTick: Int,
        var battleId: UUID? = null,
        var fainted: Boolean = false,
        var captureActive: Boolean = false,
        var captureTick: Int? = null,
        var settleAtTick: Int? = null,
        var player: ServerPlayer? = null,
        var speciesEventSeen: Boolean = false,
        val speciesStacks: MutableList<ItemStack> = mutableListOf(),
        val capturedStacks: MutableList<ItemStack> = mutableListOf()
    )

    private val activeWatches = mutableMapOf<UUID, Watch>()

    fun register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            if (!LootMenuConfig.values.enableWildPokemonLoot) return@subscribe

            alphaEntities(event.battle).forEach { entity ->
                ensureWatch(entity)?.battleId = event.battle.battleId
            }
        }

        // run before cobblemon's faint callbacks
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.HIGHEST) { event ->
            if (!LootMenuConfig.values.enableWildPokemonLoot) return@subscribe

            val entity = event.killed.entity ?: return@subscribe
            if (!entity.pokemon.isWild() || !entity.pokemon.isAlpha) return@subscribe

            val watch = ensureWatch(entity) ?: return@subscribe
            watch.battleId = event.battle.battleId
            watch.fainted = true
            watch.position = entity.position()
            watch.captureActive = true
            watch.captureTick = watch.level.server.tickCount

            val battlePlayers = event.battle.players.distinctBy { player -> player.uuid }
            if (watch.player == null && battlePlayers.size == 1) {
                watch.player = battlePlayers.single()
            }
        }


        // stop right after cobblemon's faint callbacks
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.LOWEST) { event ->
            val entity = event.killed.entity ?: return@subscribe
            if (!entity.pokemon.isWild() || !entity.pokemon.isAlpha) return@subscribe

            activeWatches[entity.uuid]?.let { watch ->
                watch.captureActive = false
                watch.captureTick = null
            }
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, level ->
            if (!LootMenuConfig.values.enableWildPokemonLoot) return@register
            if (entity !is ItemEntity || activeWatches.isEmpty()) return@register

            val serverLevel = level as? ServerLevel ?: return@register
            val currentTick = serverLevel.server.tickCount
            val match = activeWatches.entries
                .mapNotNull { entry ->
                    val watch = entry.value
                    if (watch.level != serverLevel) return@mapNotNull null
                    if (!watch.captureActive || watch.captureTick != currentTick) return@mapNotNull null

                    val distanceSq = entity.position().distanceToSqr(watch.position)
                    if (distanceSq <= CAPTURE_RADIUS_SQ) entry to distanceSq else null
                }
                .minByOrNull { (_, distanceSq) -> distanceSq }
                ?.first
                ?: return@register

            val stack = entity.item.copy()
            if (stack.isEmpty) return@register

            match.value.capturedStacks += stack
            entity.discard()

            LootMenuLog.diagnostic(
                "[alpha] caught {} x{} for {}",
                stack.hoverName.string,
                stack.count,
                match.key
            )
        }

        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            val battleId = event.battle.battleId
            activeWatches
                .filterValues { watch -> watch.battleId == battleId }
                .keys
                .toList()
                .forEach(::release)
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            val winningPlayerIds = event.winners
                .flatMap { actor -> actor.getPlayerUUIDs().toList() }
                .distinct()
            val battleId = event.battle.battleId
            val watchedEntities = activeWatches
                .filterValues { watch -> watch.battleId == battleId }
                .keys
                .toList()

            watchedEntities.forEach { entityUuid ->
                val watch = activeWatches[entityUuid] ?: return@forEach
                if (!watch.fainted) {
                    release(entityUuid)
                    return@forEach
                }

                if (watch.player == null) {
                    watch.player = singleWinningPlayer(winningPlayerIds, watch.level)
                }

                if (!watch.speciesEventSeen) {
                    scheduleSettlement(watch, VICTORY_FALLBACK_SETTLE_TICKS)
                }
                watch.expiresAtTick = watch.level.server.tickCount + POST_VICTORY_FALLBACK_TICKS
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (activeWatches.isEmpty()) return@register

            val settled = activeWatches
                .filterValues { watch ->
                    watch.settleAtTick?.let { settleAt -> settleAt <= server.tickCount } == true
                }
                .keys
                .toList()

            settled.forEach { entityUuid ->
                activeWatches.remove(entityUuid)?.let { watch ->
                    resolve(entityUuid, watch)
                }
            }

            val expired = activeWatches
                .filterValues { watch -> watch.expiresAtTick <= server.tickCount }
                .keys
                .toList()

            expired.forEach { entityUuid ->
                activeWatches.remove(entityUuid)?.let { watch ->
                    LootMenuLog.diagnostic(
                        "[alpha] watch expired for {}; dropping {} stacks",
                        entityUuid,
                        watch.speciesStacks.size + watch.capturedStacks.size
                    )
                    dropAll(watch)
                }
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            activeWatches.values.forEach(::dropAll)
            activeWatches.clear()
        }
    }

    fun stageSpeciesLoot(
        entity: PokemonEntity,
        player: ServerPlayer,
        stacks: List<ItemStack>
    ) {
        val watch = ensureWatch(entity) ?: run {
            stacks.forEach { stack ->
                if (!stack.isEmpty) player.drop(stack.copy(), false)
            }
            return
        }

        watch.player = player
        watch.position = entity.position()
        watch.speciesEventSeen = true
        watch.speciesStacks += stacks.filterNot { it.isEmpty }.map(ItemStack::copy)
        scheduleSettlement(watch, SETTLE_TICKS)

        LootMenuLog.diagnostic(
            "[alpha] staged {} species stacks for {}",
            stacks.size,
            entity.uuid
        )
    }

    fun playerFor(entityUuid: UUID): ServerPlayer? = activeWatches[entityUuid]?.player

    fun release(entityUuid: UUID) {
        activeWatches.remove(entityUuid)?.let { watch ->
            dropAll(watch)
            LootMenuLog.diagnostic("[alpha] released captured loot for {}", entityUuid)
        }
    }

    fun isWatching(entityUuid: UUID): Boolean = activeWatches.containsKey(entityUuid)

    private fun ensureWatch(entity: PokemonEntity): Watch? {
        val level = entity.level() as? ServerLevel ?: return null
        return activeWatches.getOrPut(entity.uuid) {
            LootMenuLog.diagnostic(
                "[alpha] watching {} ({})",
                entity.pokemon.getDisplayName(false).string,
                entity.uuid
            )
            Watch(
                level = level,
                position = entity.position(),
                pokemonName = entity.pokemon.getDisplayName(false),
                expiresAtTick = level.server.tickCount + MAX_WATCH_TICKS
            )
        }
    }

    private fun scheduleSettlement(watch: Watch, delayTicks: Int) {
        val settleAt = watch.level.server.tickCount + delayTicks
        watch.settleAtTick = maxOf(watch.settleAtTick ?: settleAt, settleAt)
    }

    private fun resolve(entityUuid: UUID, watch: Watch) {
        val combined = (watch.speciesStacks + watch.capturedStacks)
            .filterNot { stack -> stack.isEmpty }
            .map(ItemStack::copy)

        if (combined.isEmpty()) return

        val player = watch.player
        if (player == null) {
            LootMenuLog.diagnostic(
                "[alpha] no unambiguous player for {}; dropping {} stacks",
                entityUuid,
                combined.size
            )
            dropStacks(watch.level, watch.position, combined)
            return
        }

        val bossCompatReady = CompatDiagnostics.wildBossesIntegration.registered
        if (bossCompatReady && WildBossesLootBridge.isBoss(entityUuid)) {
            WildBossesLootBridge.stashSpeciesLoot(
                entityUuid = entityUuid,
                player = player,
                level = watch.level,
                dropPosition = watch.position,
                speciesName = watch.pokemonName,
                stacks = combined
            )
            return
        }

        CobblemonLootMenuApi.enqueue(
            LootMenuRequest(
                player = player,
                level = watch.level,
                dropPosition = watch.position,
                title = Component.translatable(
                    "screen.cobblemon_loot_menu.alpha_title",
                    watch.pokemonName
                ),
                stacks = combined,
                sourceId = LootSources.ALPHA_POKEMON
            )
        )

        LootMenuLog.diagnostic(
            "[alpha] queued {} combined stacks for {}",
            combined.size,
            entityUuid
        )
    }

    private fun alphaEntities(battle: PokemonBattle): List<PokemonEntity> =
        battle.actors
            .flatMap { actor -> actor.pokemonList }
            .mapNotNull { battlePokemon -> battlePokemon.entity }
            .filter { entity -> entity.pokemon.isWild() && entity.pokemon.isAlpha }
            .distinctBy { entity -> entity.uuid }

    private fun singleWinningPlayer(playerIds: List<UUID>, level: ServerLevel): ServerPlayer? {
        if (playerIds.size != 1) return null
        return level.server.playerList.getPlayer(playerIds.single())
    }

    private fun dropAll(watch: Watch) {
        dropStacks(
            watch.level,
            watch.position,
            watch.speciesStacks + watch.capturedStacks
        )
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

    private const val CAPTURE_RADIUS = 3.0
    private const val CAPTURE_RADIUS_SQ = CAPTURE_RADIUS * CAPTURE_RADIUS
    private const val SETTLE_TICKS = 5
    private const val VICTORY_FALLBACK_SETTLE_TICKS = 20
    private const val POST_VICTORY_FALLBACK_TICKS = 100
    private const val MAX_WATCH_TICKS = 20 * 60 * 30
}
