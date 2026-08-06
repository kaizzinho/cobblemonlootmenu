package dev.cobblemonlootmenu.network

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

data class LootActionPayload(
    val sessionId: UUID,
    val action: LootAction,
    val indices: IntArray = intArrayOf()
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<LootActionPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<LootActionPayload>(
            CobblemonLootMenuConstants.id("loot_action")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, LootActionPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, LootActionPayload> {
                override fun encode(buffer: RegistryFriendlyByteBuf, value: LootActionPayload) {
                    buffer.writeUUID(value.sessionId)
                    buffer.writeEnum(value.action)
                    buffer.writeVarIntArray(value.indices)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): LootActionPayload {
                    return LootActionPayload(
                        sessionId = buffer.readUUID(),
                        action = buffer.readEnum(LootAction::class.java),
                        indices = buffer.readVarIntArray(MAX_INDICES)
                    )
                }
            }

        private const val MAX_INDICES = 256
    }
}
