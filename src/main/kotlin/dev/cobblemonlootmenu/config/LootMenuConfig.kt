package dev.cobblemonlootmenu.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

object LootMenuConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: Path = FabricLoader.getInstance()
        .configDir
        .resolve("cobblemon-loot-menu")
    private val configPath: Path = configDir.resolve("config.json")
    private val legacyConfigPath: Path = FabricLoader.getInstance()
        .configDir
        .resolve("cobblemon_loot_menu.json")

    @Volatile
    var values: Values = Values()
        private set

    fun load() {
        val defaults = Values()
        val sourcePath = when {
            Files.exists(configPath) -> configPath
            Files.exists(legacyConfigPath) -> legacyConfigPath
            else -> null
        }

        if (sourcePath == null) {
            values = defaults
            save(defaults)
            return
        }

        var loadedLegacyConfig = false
        values = try {
            val json = JsonParser.parseString(Files.readString(sourcePath)).asJsonObject
            loadedLegacyConfig = sourcePath == legacyConfigPath
            readValues(json, defaults).sanitized()
        } catch (error: Exception) {
            CobblemonLootMenuConstants.LOGGER.error(
                "Failed to read $sourcePath; resetting it to defaults",
                error
            )
            defaults
        }

        save(values)

        if (loadedLegacyConfig) {
            runCatching { Files.deleteIfExists(legacyConfigPath) }
                .onFailure { error ->
                    CobblemonLootMenuConstants.LOGGER.warn(
                        "Failed to remove legacy config $legacyConfigPath",
                        error
                    )
                }
        }
    }

    private fun readValues(json: JsonObject, defaults: Values): Values {
        return Values(
            sessionTimeoutSeconds = json.int("sessionTimeoutSeconds", defaults.sessionTimeoutSeconds),
            maxQueuedSessions = json.int("maxQueuedSessions", defaults.maxQueuedSessions),
            adminCommandPermissionLevel = json.int(
                "adminCommandPermissionLevel",
                defaults.adminCommandPermissionLevel
            ),
            enableWildPokemonLoot = json.bool(
                "enableWildPokemonLoot",
                defaults.enableWildPokemonLoot
            ),
            enableWildBossesCompat = json.bool(
                "enableWildBossesCompat",
                defaults.enableWildBossesCompat
            ),
            enableRctCompat = json.bool("enableRctCompat", defaults.enableRctCompat),
            diagnosticLogging = json.bool("diagnosticLogging", defaults.diagnosticLogging),
            showQueueIndicator = json.bool("showQueueIndicator", defaults.showQueueIndicator),
            showInventoryOverflowMessage = json.bool(
                "showInventoryOverflowMessage",
                defaults.showInventoryOverflowMessage
            ),
            rctWatchTicks = json.int("rctWatchTicks", defaults.rctWatchTicks),
            rctCaptureRadius = json.double("rctCaptureRadius", defaults.rctCaptureRadius),
            wildBossStashTimeoutSeconds = json.int(
                "wildBossStashTimeoutSeconds",
                defaults.wildBossStashTimeoutSeconds
            )
        )
    }

    private fun save(config: Values) {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (error: Exception) {
            CobblemonLootMenuConstants.LOGGER.error("Failed to write $configPath", error)
        }
    }

    private fun JsonObject.int(name: String, fallback: Int): Int =
        runCatching { get(name)?.asInt }.getOrNull() ?: fallback

    private fun JsonObject.double(name: String, fallback: Double): Double =
        runCatching { get(name)?.asDouble }.getOrNull() ?: fallback

    private fun JsonObject.bool(name: String, fallback: Boolean): Boolean =
        runCatching { get(name)?.asBoolean }.getOrNull() ?: fallback

    data class Values(
        val sessionTimeoutSeconds: Int = 60,
        val maxQueuedSessions: Int = 16,
        val adminCommandPermissionLevel: Int = 2,
        val enableWildPokemonLoot: Boolean = true,
        val enableWildBossesCompat: Boolean = true,
        val enableRctCompat: Boolean = true,
        val diagnosticLogging: Boolean = false,
        val showQueueIndicator: Boolean = true,
        val showInventoryOverflowMessage: Boolean = true,
        val rctWatchTicks: Int = 20,
        val rctCaptureRadius: Double = 4.0,
        val wildBossStashTimeoutSeconds: Int = 60
    ) {
        fun sanitized(): Values = copy(
            sessionTimeoutSeconds = sessionTimeoutSeconds.coerceIn(10, 3600),
            maxQueuedSessions = maxQueuedSessions.coerceIn(1, 256),
            adminCommandPermissionLevel = adminCommandPermissionLevel.coerceIn(0, 4),
            rctWatchTicks = rctWatchTicks.coerceIn(1, 200),
            rctCaptureRadius = rctCaptureRadius.coerceIn(1.0, 16.0),
            wildBossStashTimeoutSeconds = wildBossStashTimeoutSeconds.coerceIn(5, 600)
        )
    }
}
