package dev.cobblemonlootmenu.api

import dev.cobblemonlootmenu.server.PendingLootManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

object CobblemonLootMenuApi {
    // queues loot without exposing the internal session state
    @JvmStatic
    fun enqueue(request: LootMenuRequest): LootEnqueueResult {
        return PendingLootManager.enqueue(
            player = request.player,
            level = request.level,
            dropPosition = request.dropPosition,
            title = request.title,
            stacks = request.stacks,
            sourceId = request.sourceId
        )
    }

    @JvmStatic
    fun enqueue(
        player: ServerPlayer,
        level: ServerLevel,
        dropPosition: Vec3,
        title: Component,
        stacks: List<ItemStack>,
        sourceId: ResourceLocation
    ): LootEnqueueResult {
        return enqueue(
            LootMenuRequest(
                player = player,
                level = level,
                dropPosition = dropPosition,
                title = title,
                stacks = stacks,
                sourceId = sourceId
            )
        )
    }
}
