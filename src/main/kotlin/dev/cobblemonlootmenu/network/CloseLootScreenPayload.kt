package dev.cobblemonlootmenu.network

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

data class CloseLootScreenPayload(
    val sessionId: UUID
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CloseLootScreenPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CloseLootScreenPayload>(
            CobblemonLootMenuConstants.id("close_loot_screen")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CloseLootScreenPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, CloseLootScreenPayload> {
                override fun encode(buffer: RegistryFriendlyByteBuf, value: CloseLootScreenPayload) {
                    buffer.writeUUID(value.sessionId)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): CloseLootScreenPayload {
                    return CloseLootScreenPayload(buffer.readUUID())
                }
            }
    }
}
