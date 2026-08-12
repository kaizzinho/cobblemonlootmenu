package dev.cobblemonlootmenu.api

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

data class LootMenuRequest(
    val player: ServerPlayer,
    val level: ServerLevel,
    val dropPosition: Vec3,
    val title: Component,
    val stacks: List<ItemStack>,
    val sourceId: ResourceLocation
)
