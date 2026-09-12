package dev.cobblemonlootmenu

import dev.cobblemonlootmenu.command.LootMenuCommands
import dev.cobblemonlootmenu.compat.CompatInit
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.network.LootNetworking
import dev.cobblemonlootmenu.server.AlphaLootBridge
import dev.cobblemonlootmenu.server.CobblemonLootInterceptor
import dev.cobblemonlootmenu.server.PendingLootManager
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CobblemonLootMenuConstants {
    const val MOD_ID: String = "cobblemon_loot_menu"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}

class CobblemonLootMenu : ModInitializer {
    override fun onInitialize() {
        LootMenuConfig.load()
        LootNetworking.registerPayloadTypes()
        LootNetworking.registerServerReceiver()
        PendingLootManager.registerLifecycleEvents()
        AlphaLootBridge.register()
        CobblemonLootInterceptor.register()
        CompatInit.register()
        LootMenuCommands.register()

        CobblemonLootMenuConstants.LOGGER.info("Cobblemon Loot Menu initialized")
    }
}
