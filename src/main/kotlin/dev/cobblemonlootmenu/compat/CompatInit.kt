package dev.cobblemonlootmenu.compat

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import dev.cobblemonlootmenu.compat.rct.RctLootCatcher
import dev.cobblemonlootmenu.compat.wildbosses.WildBossesLootBridge
import dev.cobblemonlootmenu.config.LootMenuConfig
import net.fabricmc.loader.api.FabricLoader

object CompatInit {
    fun register() {
        val loader = FabricLoader.getInstance()

        CompatDiagnostics.wildBossesIntegration.installed = loader.isModLoaded("wildbosses")
        CompatDiagnostics.wildBossesIntegration.enabled =
            LootMenuConfig.values.enableWildBossesCompat

        if (
            CompatDiagnostics.wildBossesIntegration.installed &&
            CompatDiagnostics.wildBossesIntegration.enabled
        ) {
            WildBossesLootBridge.register()
            CompatDiagnostics.wildBossesIntegration.registered = true
            CobblemonLootMenuConstants.LOGGER.info("WildBosses loot compat enabled")
        }

        CompatDiagnostics.rctIntegration.installed = loader.isModLoaded("rctmod")
        CompatDiagnostics.rctIntegration.enabled = LootMenuConfig.values.enableRctCompat

        if (
            CompatDiagnostics.rctIntegration.installed &&
            CompatDiagnostics.rctIntegration.enabled
        ) {
            RctLootCatcher.register()
            CompatDiagnostics.rctIntegration.registered = true
            CobblemonLootMenuConstants.LOGGER.info("RCT loot compat enabled")
        }
    }
}
