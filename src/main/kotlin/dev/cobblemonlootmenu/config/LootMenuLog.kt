package dev.cobblemonlootmenu.config

import dev.cobblemonlootmenu.CobblemonLootMenuConstants

object LootMenuLog {
    fun diagnostic(message: String, vararg args: Any?) {
        if (LootMenuConfig.values.diagnosticLogging) {
            CobblemonLootMenuConstants.LOGGER.info(message, *args)
        } else {
            CobblemonLootMenuConstants.LOGGER.debug(message, *args)
        }
    }
}
