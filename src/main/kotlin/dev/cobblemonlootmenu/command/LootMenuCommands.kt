package dev.cobblemonlootmenu.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.cobblemonlootmenu.api.CobblemonLootMenuApi
import dev.cobblemonlootmenu.api.LootMenuRequest
import dev.cobblemonlootmenu.api.LootSources
import dev.cobblemonlootmenu.compat.CompatDiagnostics
import dev.cobblemonlootmenu.config.LootMenuConfig
import dev.cobblemonlootmenu.server.PendingLootManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object LootMenuCommands {
    private val testItems: List<Item> = listOf(
        Items.DIAMOND,
        Items.EMERALD,
        Items.GOLD_INGOT,
        Items.IRON_INGOT,
        Items.COPPER_INGOT,
        Items.REDSTONE,
        Items.LAPIS_LAZULI,
        Items.QUARTZ,
        Items.AMETHYST_SHARD,
        Items.BLAZE_ROD,
        Items.ENDER_PEARL,
        Items.PRISMARINE_SHARD,
        Items.GLOWSTONE_DUST,
        Items.COAL,
        Items.EXPERIENCE_BOTTLE,
        Items.SLIME_BALL
    )

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("lootmenu")
                    .requires { source ->
                        source.hasPermission(LootMenuConfig.values.adminCommandPermissionLevel)
                    }
                    .then(
                        Commands.literal("test")
                            .executes { context -> openTest(context.source, 20) }
                            .then(
                                Commands.argument(
                                    "count",
                                    IntegerArgumentType.integer(1, 256)
                                ).executes { context ->
                                    openTest(
                                        context.source,
                                        IntegerArgumentType.getInteger(context, "count")
                                    )
                                }
                            )
                    )
                    .then(
                        Commands.literal("pending")
                            .executes { context -> showPending(context.source) }
                    )
                    .then(
                        Commands.literal("compat")
                            .executes { context -> showCompat(context.source) }
                    )
            )
        }
    }

    private fun openTest(source: CommandSourceStack, count: Int): Int {
        val player = source.playerOrException
        val stacks = List(count) { index ->
            ItemStack(testItems[index % testItems.size], index % 16 + 1)
        }

        val result = CobblemonLootMenuApi.enqueue(
            LootMenuRequest(
                player = player,
                level = player.serverLevel(),
                dropPosition = player.position()
                    .add(player.lookAngle.scale(2.0))
                    .add(0.0, 0.25, 0.0),
                title = Component.translatable("screen.cobblemon_loot_menu.test_title"),
                stacks = stacks,
                sourceId = LootSources.TEST
            )
        )

        source.sendSuccess(
            {
                Component.translatable(
                    "command.cobblemon_loot_menu.test_result",
                    count,
                    result.name.lowercase()
                )
            },
            false
        )
        return 1
    }

    private fun showPending(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val status = PendingLootManager.getStatus(player.uuid, source.server.tickCount)

        if (status.queueSize == 0) {
            source.sendSuccess(
                { Component.translatable("command.cobblemon_loot_menu.pending_none") },
                false
            )
            return 1
        }

        val seconds = (status.expiresInTicks + 19) / 20
        source.sendSuccess(
            {
                Component.translatable(
                    "command.cobblemon_loot_menu.pending_status",
                    status.queueSize,
                    status.unresolvedStacks,
                    seconds,
                    status.sourceId?.toString() ?: "-",
                    status.activeSessionId?.toString() ?: "-"
                )
            },
            false
        )
        return 1
    }

    private fun showCompat(source: CommandSourceStack): Int {
        val wild = CompatDiagnostics.wildBossesIntegration
        val wildStats = CompatDiagnostics.wildBosses
        source.sendSuccess(
            {
                Component.translatable(
                    "command.cobblemon_loot_menu.compat_wildbosses",
                    wild.installed,
                    wild.enabled,
                    wild.registered,
                    wildStats.awardsObserved,
                    wildStats.activeStashes,
                    wildStats.mergedAwards,
                    wildStats.lastBossName
                )
            },
            false
        )

        val rct = CompatDiagnostics.rctIntegration
        val rctStats = CompatDiagnostics.rct
        source.sendSuccess(
            {
                Component.translatable(
                    "command.cobblemon_loot_menu.compat_rct",
                    rct.installed,
                    rct.enabled,
                    rct.registered,
                    rctStats.trainerVictoriesObserved,
                    rctStats.capturedStacks,
                    rctStats.activeWatches,
                    rctStats.emptyWatches,
                    rctStats.lastTrainerName,
                    rctStats.lastCaptureSize
                )
            },
            false
        )
        return 1
    }
}
