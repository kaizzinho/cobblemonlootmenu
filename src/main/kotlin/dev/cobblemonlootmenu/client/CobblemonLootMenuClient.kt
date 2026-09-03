package dev.cobblemonlootmenu.client

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import dev.cobblemonlootmenu.client.gui.LootSelectionScreen
import dev.cobblemonlootmenu.network.CloseLootScreenPayload
import dev.cobblemonlootmenu.network.OpenLootScreenPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

class CobblemonLootMenuClient : ClientModInitializer {
    private var pendingOpenPayload: OpenLootScreenPayload? = null
    private var clearScreenTicks = 0

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
                if (pendingOpenPayload?.sessionId != payload.sessionId) {
                    clearScreenTicks = 0
                }
                pendingOpenPayload = payload
                CobblemonLootMenuConstants.LOGGER.debug(
                    "Client queued loot screen payload: session={}",
                    payload.sessionId
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(CloseLootScreenPayload.TYPE) { payload, context ->
            if (pendingOpenPayload?.sessionId == payload.sessionId) {
                clearPendingOpen()
            }

            val currentScreen = context.client().screen
            if (currentScreen is LootSelectionScreen && currentScreen.sessionId == payload.sessionId) {
                currentScreen.markResolvedByServer()
                context.client().setScreen(null)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            processPendingOpen(client)
        }

        CobblemonLootMenuConstants.LOGGER.info("Cobblemon Loot Menu client initialized")
    }

    private fun processPendingOpen(client: Minecraft) {
        val payload = pendingOpenPayload ?: return

        if (client.level == null || client.player == null) {
            clearPendingOpen()
            return
        }

        val currentScreen = client.screen
        if (currentScreen is LootSelectionScreen) {
            if (currentScreen.sessionId == payload.sessionId) {
                currentScreen.updateFromServer(payload)
                clearPendingOpen()
            } else {
                clearScreenTicks = 0
            }
            return
        }

        if (currentScreen != null) {
            clearScreenTicks = 0
            return
        }

        clearScreenTicks++
        if (clearScreenTicks < REQUIRED_CLEAR_SCREEN_TICKS) {
            return
        }

        CobblemonLootMenuConstants.LOGGER.debug(
            "Client opening deferred loot screen: session={}",
            payload.sessionId
        )
        client.setScreen(LootSelectionScreen(payload))
        clearPendingOpen()
    }

    private fun clearPendingOpen() {
        pendingOpenPayload = null
        clearScreenTicks = 0
    }

    private companion object {
        const val REQUIRED_CLEAR_SCREEN_TICKS = 2
    }
}
