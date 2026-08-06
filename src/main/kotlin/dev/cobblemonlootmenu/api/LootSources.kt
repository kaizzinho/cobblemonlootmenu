package dev.cobblemonlootmenu.api

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.minecraft.resources.ResourceLocation

object LootSources {
    val WILD_POKEMON: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        "cobblemon",
        "wild_pokemon"
    )
    val WILD_BOSS: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        "wildbosses",
        "boss"
    )
    val RCT_TRAINER: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        "rctmod",
        "trainer"
    )
    val TEST: ResourceLocation = CobblemonLootMenuConstants.id("test")
}
