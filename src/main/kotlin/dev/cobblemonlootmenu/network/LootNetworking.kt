package dev.cobblemonlootmenu.network

import dev.cobblemonlootmenu.server.PendingLootManager
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

object LootNetworking {

    fun registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(
            OpenLootScreenPayload.TYPE,
            OpenLootScreenPayload.STREAM_CODEC
        )
        PayloadTypeRegistry.playS2C().register(
            CloseLootScreenPayload.TYPE,
            CloseLootScreenPayload.STREAM_CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            LootActionPayload.TYPE,
            LootActionPayload.STREAM_CODEC
        )
    }

    fun registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(LootActionPayload.TYPE) { payload, context ->
            PendingLootManager.handleAction(
                server = context.server(),
                player = context.player(),
                payload = payload
            )
        }
    }
}
