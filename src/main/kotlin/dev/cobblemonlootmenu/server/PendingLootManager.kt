package dev.cobblemonlootmenu.server

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import dev.cobblemonlootmenu.api.LootEnqueueResult
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.network.CloseLootScreenPayload
import dev.cobblemonlootmenu.network.LootAction
import dev.cobblemonlootmenu.network.LootActionPayload
import dev.cobblemonlootmenu.network.OpenLootScreenPayload
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.UUID

object PendingLootManager {
    private const val MAX_STACKS_PER_SESSION = 256
    private const val TEST_DROP_PICKUP_DELAY_TICKS = 60
    private const val TEST_DROP_SPREAD = 0.15
    private val queues = mutableMapOf<UUID, ArrayDeque<PendingLootSession>>()

    fun registerLifecycleEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            resolveDisconnectedPlayer(server, handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            val pending = queues.values.flatMap { it.toList() }
            queues.clear()
            pending.forEach { session -> dropUnresolved(server, session) }
        }
    }

    fun enqueue(
        player: ServerPlayer,
        level: ServerLevel,
        dropPosition: Vec3,
        title: Component,
        stacks: List<ItemStack>,
        sourceId: ResourceLocation
    ): LootEnqueueResult {
        val preparedStacks = stacks.filterNot { it.isEmpty }.map(ItemStack::copy)
        if (preparedStacks.isEmpty()) return LootEnqueueResult.EMPTY
        if (preparedStacks.size > MAX_STACKS_PER_SESSION) {
            CobblemonLootMenuConstants.LOGGER.warn(
                "Loot request from {} had {} stacks; max is {}. Dropping it at the origin",
                sourceId,
                preparedStacks.size,
                MAX_STACKS_PER_SESSION
            )
            preparedStacks.forEach {
                dropStack(level, dropPosition, it, sourceId == LootSources.TEST)
            }
            return LootEnqueueResult.STACK_LIMIT_DROPPED
        }

        val queue = queues.getOrPut(player.uuid) { ArrayDeque() }
        if (queue.size >= LootMenuConfig.values.maxQueuedSessions) {
            CobblemonLootMenuConstants.LOGGER.warn(
                "Loot queue for {} reached {}; dropping the new loot",
                player.gameProfile.name,
                LootMenuConfig.values.maxQueuedSessions
            )
            preparedStacks.forEach {
                dropStack(level, dropPosition, it, sourceId == LootSources.TEST)
            }
            return LootEnqueueResult.QUEUE_FULL_DROPPED
        }

        val server = level.server
        val becomesActive = queue.isEmpty()
        val session = PendingLootSession(
            id = UUID.randomUUID(),
            ownerId = player.uuid,
            dimension = level.dimension(),
            dropPosition = dropPosition,
            title = title,
            sourceId = sourceId,
            stacks = preparedStacks.toMutableList(),
            taken = BooleanArray(preparedStacks.size),
            expiresAtTick = if (becomesActive) {
                server.tickCount + timeoutTicks()
            } else {
                Int.MAX_VALUE
            }
        )

        queue.addLast(session)
        if (becomesActive) {
            sendSnapshotOrResolve(server, player, session)
        } else {
            queue.peekFirst()?.let { active ->
                sendSnapshotOrResolve(server, player, active)
            }
        }
        return LootEnqueueResult.ENQUEUED
    }

    fun handleAction(
        server: MinecraftServer,
        player: ServerPlayer,
        payload: LootActionPayload
    ) {
        val queue = queues[player.uuid] ?: return
        val session = queue.peekFirst() ?: return
        if (session.id != payload.sessionId) return

        session.expiresAtTick = server.tickCount + timeoutTicks()

        when (payload.action) {
            LootAction.TAKE_ONE -> {
                val index = payload.indices.singleOrNull() ?: return
                grantIndices(player, session, intArrayOf(index))
            }

            LootAction.TAKE_SELECTED -> grantIndices(player, session, payload.indices)
            LootAction.TAKE_ALL -> grantIndices(player, session, session.unresolvedIndices())
            LootAction.DROP_ALL -> dropUnresolved(server, session)
            LootAction.DISCARD_ALL -> discardUnresolved(session)
        }

        if (
            payload.action == LootAction.DROP_ALL ||
            payload.action == LootAction.DISCARD_ALL ||
            session.isResolved()
        ) {
            finishHead(server, player.uuid, notifyClient = true)
        } else {
            sendSnapshotOrResolve(server, player, session)
        }
    }

    fun getStatus(ownerId: UUID, currentTick: Int): PendingLootStatus {
        val queue = queues[ownerId]
        val active = queue?.peekFirst()
        return PendingLootStatus(
            queueSize = queue?.size ?: 0,
            activeSessionId = active?.id,
            sourceId = active?.sourceId,
            unresolvedStacks = active?.unresolvedCount() ?: 0,
            expiresInTicks = active?.let { (it.expiresAtTick - currentTick).coerceAtLeast(0) } ?: 0
        )
    }

    private fun grantIndices(
        player: ServerPlayer,
        session: PendingLootSession,
        requestedIndices: IntArray
    ) {
        var droppedStacks = 0
        var droppedItems = 0

        requestedIndices
            .asSequence()
            .distinct()
            .filter { it in session.stacks.indices }
            .filterNot { session.taken[it] }
            .forEach { index ->
                val remainder = session.stacks[index].copy()
                player.addItem(remainder)

                if (!remainder.isEmpty) {
                    droppedStacks++
                    droppedItems += remainder.count
                    player.drop(remainder, false)
                }

                session.taken[index] = true
            }

        if (
            droppedStacks > 0 &&
            LootMenuConfig.values.showInventoryOverflowMessage
        ) {
            player.displayClientMessage(
                Component.translatable(
                    "message.cobblemon_loot_menu.inventory_overflow",
                    droppedStacks,
                    droppedItems
                ),
                true
            )
        }
    }

    private fun sendSnapshotOrResolve(
        server: MinecraftServer,
        player: ServerPlayer,
        session: PendingLootSession
    ) {
        if (!ServerPlayNetworking.canSend(player, OpenLootScreenPayload.TYPE)) {
            CobblemonLootMenuConstants.LOGGER.warn(
                "Client {} cannot receive loot menu packets; dropping pending loot",
                player.gameProfile.name
            )
            dropUnresolved(server, session)
            finishHead(server, player.uuid, notifyClient = false)
            return
        }

        val queueSize = queues[player.uuid]?.size ?: 1
        val visibleQueueSize = if (LootMenuConfig.values.showQueueIndicator) queueSize else 1

        ServerPlayNetworking.send(
            player,
            OpenLootScreenPayload(
                sessionId = session.id,
                title = session.title,
                sourceId = session.sourceId,
                stacks = session.stacks.map(ItemStack::copy),
                taken = session.taken.copyOf(),
                queueSize = visibleQueueSize
            )
        )
    }

    private fun finishHead(
        server: MinecraftServer,
        ownerId: UUID,
        notifyClient: Boolean
    ) {
        val queue = queues[ownerId] ?: return
        val finished = queue.pollFirst() ?: return
        val player = server.playerList.getPlayer(ownerId)

        if (
            notifyClient &&
            player != null &&
            ServerPlayNetworking.canSend(player, CloseLootScreenPayload.TYPE)
        ) {
            ServerPlayNetworking.send(player, CloseLootScreenPayload(finished.id))
        }

        val next = queue.peekFirst()
        if (next == null) {
            queues.remove(ownerId)
        } else if (player != null) {
            next.expiresAtTick = server.tickCount + timeoutTicks()
            sendSnapshotOrResolve(server, player, next)
        } else {
            val abandoned = queue.toList()
            queues.remove(ownerId)
            abandoned.forEach { session -> dropUnresolved(server, session) }
        }
    }

    private fun resolveDisconnectedPlayer(server: MinecraftServer, ownerId: UUID) {
        val queue = queues.remove(ownerId) ?: return
        queue.forEach { session -> dropUnresolved(server, session) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.tickCount % 20 != 0) return

        val owners = queues.keys.toList()
        owners.forEach { ownerId ->
            val queue = queues[ownerId] ?: return@forEach
            val current = queue.peekFirst() ?: return@forEach
            if (server.tickCount < current.expiresAtTick) return@forEach

            CobblemonLootMenuConstants.LOGGER.debug(
                "Loot session {} timed out for player {}",
                current.id,
                ownerId
            )
            dropUnresolved(server, current)
            finishHead(server, ownerId, notifyClient = true)
        }
    }

    private fun dropUnresolved(server: MinecraftServer, session: PendingLootSession) {
        val level = resolveLevel(server, session)
        session.stacks.indices
            .filterNot { session.taken[it] }
            .forEach { index ->
                dropStack(
                    level,
                    session.dropPosition,
                    session.stacks[index],
                    session.sourceId == LootSources.TEST
                )
                session.taken[index] = true
            }
    }

    private fun discardUnresolved(session: PendingLootSession) {
        session.stacks.indices
            .filterNot { session.taken[it] }
            .forEach { index -> session.taken[index] = true }
    }

    private fun resolveLevel(
        server: MinecraftServer,
        session: PendingLootSession
    ): ServerLevel {
        return server.getLevel(session.dimension)
            ?: server.getLevel(Level.OVERWORLD)
            ?: error("Minecraft server has no overworld")
    }

    private fun dropStack(
        level: ServerLevel,
        position: Vec3,
        original: ItemStack,
        spreadForTest: Boolean = false
    ) {
        if (original.isEmpty) return

        val entity = ItemEntity(
            level,
            position.x,
            position.y + if (spreadForTest) 0.25 else 0.0,
            position.z,
            original.copy()
        )

        if (spreadForTest) {
            // makes /lootmenu test drops easy to see without changing real battle loot
            entity.setPickUpDelay(TEST_DROP_PICKUP_DELAY_TICKS)
            entity.setDeltaMovement(
                (level.random.nextDouble() - 0.5) * TEST_DROP_SPREAD,
                0.2,
                (level.random.nextDouble() - 0.5) * TEST_DROP_SPREAD
            )
        }

        level.addFreshEntity(entity)
    }

    private fun timeoutTicks(): Int = LootMenuConfig.values.sessionTimeoutSeconds * 20
}
