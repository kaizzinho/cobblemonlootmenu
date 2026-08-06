package dev.cobblemonlootmenu.client.gui

import dev.cobblemonlootmenu.CobblemonLootMenuConstants
import net.minecraft.resources.ResourceLocation

object LootTextures {
    private fun texture(name: String): ResourceLocation =
        CobblemonLootMenuConstants.id("textures/gui/loot/$name.png")

    val PANEL = texture("loot_panel_276x180")
    val TITLE_BAR = texture("loot_title_bar_266x18")
    val GRID_WELL = texture("loot_grid_well_126x118")
    val DETAIL_WELL = texture("loot_detail_well_128x118")

    val SLOT_NORMAL = texture("loot_slot_normal_22x22")
    val SLOT_HOVER = texture("loot_slot_hover_22x22")
    val SLOT_SELECTED = texture("loot_slot_selected_22x22")
    val SLOT_TAKEN = texture("loot_slot_taken_22x22")
    val SLOT_DISABLED = texture("loot_slot_disabled_22x22")

    val SCROLL_TRACK = texture("loot_scroll_track_6x94")
    val SCROLL_GRIP = texture("loot_scroll_grip_6x22")
    val SCROLL_GRIP_HOVER = texture("loot_scroll_grip_hover_6x22")

    val BUTTON_NEUTRAL = texture("button_neutral_64x20")
    val BUTTON_NEUTRAL_HOVER = texture("button_neutral_hover_64x20")
    val BUTTON_ACCEPT = texture("button_accept_64x20")
    val BUTTON_ACCEPT_HOVER = texture("button_accept_hover_64x20")
    val BUTTON_DANGER = texture("button_danger_64x20")
    val BUTTON_DANGER_HOVER = texture("button_danger_hover_64x20")
    val BUTTON_DISABLED = texture("button_disabled_64x20")

    val ITEM_FRAME = texture("loot_item_frame_48x48")
    val QUANTITY_BADGE = texture("loot_qty_badge_28x12")
    val DIVIDER = texture("loot_divider_112x2")

    val ICON_BALL = texture("icon_ball_16x16")
    val ICON_CLOSE = texture("icon_close_9x9")
    val ICON_CLOSE_HOVER = texture("icon_close_hover_9x9")
    val ICON_CHECK = texture("icon_check_9x9")
    val ICON_NONE = texture("icon_none_12x12")
}
