package dev.cobblemonlootmenu.client

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import dev.cobblemonlootmenu.client.gui.LootSelectionScreen
import dev.cobblemonlootmenu.network.CloseLootScreenPayload
import dev.cobblemonlootmenu.network.OpenLootScreenPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

class CobblemonLootMenuClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenLootScreenPayload.TYPE) { payload, context ->
            val currentScreen = context.client().screen
            CobblemonLootMenuConstants.LOGGER.debug(
                "Client received loot screen payload: session={}, stacks={}",
                payload.sessionId,
                payload.stacks.size
            )
            if (currentScreen is LootSelectionScreen && currentScreen.sessionId == payload.sessionId) {
                currentScreen.updateFromServer(payload)
            } else {
                context.client().setScreen(LootSelectionScreen(payload))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(CloseLootScreenPayload.TYPE) { payload, context ->
            val currentScreen = context.client().screen
            if (currentScreen is LootSelectionScreen && currentScreen.sessionId == payload.sessionId) {
                currentScreen.markResolvedByServer()
                context.client().setScreen(null)
            }
        }

        CobblemonLootMenuConstants.LOGGER.info("Cobblemon Loot Menu client initialized")
    }
}
