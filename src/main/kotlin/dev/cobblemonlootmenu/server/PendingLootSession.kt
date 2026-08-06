package dev.cobblemonlootmenu.server

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

data class PendingLootSession(
    val id: UUID,
    val ownerId: UUID,
    val dimension: ResourceKey<Level>,
    val dropPosition: Vec3,
    val title: Component,
    val sourceId: ResourceLocation,
    val stacks: MutableList<ItemStack>,
    val taken: BooleanArray,
    var expiresAtTick: Int
) {
    fun unresolvedIndices(): IntArray =
        taken.indices.filterNot { taken[it] }.toIntArray()

    fun unresolvedCount(): Int = taken.count { !it }

    fun isResolved(): Boolean = taken.all { it }
}
