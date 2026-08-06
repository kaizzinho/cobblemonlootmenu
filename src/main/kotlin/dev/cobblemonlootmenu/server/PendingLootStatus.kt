package dev.cobblemonlootmenu.server

import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class PendingLootStatus(
    val queueSize: Int,
    val activeSessionId: UUID?,
    val sourceId: ResourceLocation?,
    val unresolvedStacks: Int,
    val expiresInTicks: Int
)
