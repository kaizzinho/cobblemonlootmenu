package dev.cobblemonlootmenu.network

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class OpenLootScreenPayload(
    val sessionId: UUID,
    val title: Component,
    val sourceId: ResourceLocation,
    val stacks: List<ItemStack>,
    val taken: BooleanArray,
    val queueSize: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OpenLootScreenPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OpenLootScreenPayload>(
            CobblemonLootMenuConstants.id("open_loot_screen")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenLootScreenPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, OpenLootScreenPayload> {
                override fun encode(buffer: RegistryFriendlyByteBuf, value: OpenLootScreenPayload) {
                    buffer.writeUUID(value.sessionId)
                    ComponentSerialization.STREAM_CODEC.encode(buffer, value.title)
                    buffer.writeResourceLocation(value.sourceId)
                    buffer.writeVarInt(value.stacks.size)
                    value.stacks.forEach { stack ->
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack)
                    }

                    buffer.writeVarInt(value.taken.size)
                    value.taken.forEach(buffer::writeBoolean)
                    buffer.writeVarInt(value.queueSize)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): OpenLootScreenPayload {
                    val sessionId = buffer.readUUID()
                    val title = ComponentSerialization.STREAM_CODEC.decode(buffer)
                    val sourceId = buffer.readResourceLocation()
                    val stackCount = buffer.readVarInt()
                    require(stackCount in 0..MAX_STACKS) {
                        "Invalid loot stack count: $stackCount"
                    }

                    val stacks = ArrayList<ItemStack>(stackCount)
                    repeat(stackCount) {
                        stacks += ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
                    }

                    val takenCount = buffer.readVarInt()
                    require(takenCount == stackCount) {
                        "Loot state length $takenCount does not match stack count $stackCount"
                    }

                    val taken = BooleanArray(takenCount) { buffer.readBoolean() }
                    val queueSize = buffer.readVarInt()
                    require(queueSize >= 1) {
                        "Invalid loot queue size: $queueSize"
                    }

                    return OpenLootScreenPayload(
                        sessionId = sessionId,
                        title = title,
                        sourceId = sourceId,
                        stacks = stacks,
                        taken = taken,
                        queueSize = queueSize
                    )
                }
            }

        private const val MAX_STACKS = 256
    }
}
