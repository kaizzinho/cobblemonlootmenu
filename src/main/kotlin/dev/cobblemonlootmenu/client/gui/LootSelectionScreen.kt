package dev.cobblemonlootmenu.client.gui

import dev.cobblemonlootmenu.network.LootAction
import dev.cobblemonlootmenu.network.LootActionPayload
import dev.cobblemonlootmenu.network.OpenLootScreenPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

class LootSelectionScreen(
    initialPayload: OpenLootScreenPayload
) : Screen(initialPayload.title) {

    val sessionId: UUID = initialPayload.sessionId
    val sourceId: ResourceLocation = initialPayload.sourceId

    private var stacks: List<ItemStack> = initialPayload.stacks.map(ItemStack::copy)
    private var taken: BooleanArray = initialPayload.taken.copyOf()
    private var queueSize: Int = initialPayload.queueSize
    private val selected = linkedSetOf<Int>()

    private var scrollRow = 0
    private var requestInFlight = false
    private var resolvedByServer = false

    private var panelX = 0
    private var panelY = 0

    override fun init() {
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        clampScroll()
    }

    fun updateFromServer(payload: OpenLootScreenPayload) {
        if (payload.sessionId != sessionId) return

        stacks = payload.stacks.map(ItemStack::copy)
        taken = payload.taken.copyOf()
        queueSize = payload.queueSize
        selected.removeIf { it !in stacks.indices || taken[it] }
        requestInFlight = false
        clampScroll()
    }

    fun markResolvedByServer() {
        resolvedByServer = true
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        graphics.fill(0, 0, width, height, 0xA0000000.toInt())

        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2

        blit(graphics, LootTextures.PANEL, panelX, panelY, 276, 180)
        blit(graphics, LootTextures.TITLE_BAR, panelX + 5, panelY + 5, 266, 18)
        blit(graphics, LootTextures.ICON_BALL, panelX + 9, panelY + 6, 16, 16)

        graphics.drawString(
            font,
            title,
            panelX + 29,
            panelY + 10,
            0xFFFFFF,
            true
        )

        val closeHovered = isInside(mouseX, mouseY, panelX + 254, panelY + 9, 9, 9)
        blit(
            graphics,
            if (closeHovered) LootTextures.ICON_CLOSE_HOVER else LootTextures.ICON_CLOSE,
            panelX + 254,
            panelY + 9,
            9,
            9
        )

        blit(graphics, LootTextures.GRID_WELL, panelX + 8, panelY + 28, 126, 118)
        blit(graphics, LootTextures.DETAIL_WELL, panelX + 140, panelY + 28, 128, 118)

        val hoveredIndex = renderSlots(graphics, mouseX, mouseY)
        renderCountStrip(graphics)
        renderDetail(graphics, hoveredIndex)
        renderScrollbar(graphics, mouseX, mouseY)
        renderButtons(graphics, mouseX, mouseY)

        if (hoveredIndex != null && hoveredIndex in stacks.indices) {
            graphics.renderTooltip(font, stacks[hoveredIndex], mouseX, mouseY)
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderSlots(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Int? {
        var hoveredIndex: Int? = null
        val firstIndex = scrollRow * COLUMNS
        val takenOverlays = mutableListOf<Pair<Int, Int>>()

        repeat(VISIBLE_CELLS) { visibleCell ->
            val column = visibleCell % COLUMNS
            val row = visibleCell / COLUMNS
            val index = firstIndex + visibleCell
            val slotX = panelX + 12 + column * 24
            val slotY = panelY + 32 + row * 24
            val hovered = isInside(mouseX, mouseY, slotX, slotY, 22, 22)

            val texture = when {
                index !in stacks.indices -> LootTextures.SLOT_DISABLED
                taken[index] -> LootTextures.SLOT_TAKEN
                index in selected -> LootTextures.SLOT_SELECTED
                hovered -> LootTextures.SLOT_HOVER
                else -> LootTextures.SLOT_NORMAL
            }

            blit(graphics, texture, slotX, slotY, 22, 22)

            if (index in stacks.indices) {
                val stack = stacks[index]
                graphics.renderItem(stack, slotX + 3, slotY + 3)
                graphics.renderItemDecorations(font, stack, slotX + 3, slotY + 3)

                if (taken[index]) {
                    takenOverlays += slotX to slotY
                } else if (hovered) {
                    hoveredIndex = index
                }
            }
        }

        // render these last so the item model can't cover them
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 300.0)

        takenOverlays.forEach { (slotX, slotY) ->
            graphics.fill(
                slotX + 3,
                slotY + 3,
                slotX + 19,
                slotY + 19,
                0x66000000
            )
            blit(
                graphics,
                LootTextures.ICON_CHECK,
                slotX + 12,
                slotY + 2,
                9,
                9
            )
        }

        graphics.pose().popPose()
        return hoveredIndex
    }

    private fun renderCountStrip(graphics: GuiGraphics) {
        val remaining = taken.count { !it }
        val text = if (queueSize > 1) {
            Component.translatable(
                "screen.cobblemon_loot_menu.count_with_queue",
                remaining,
                selected.size,
                queueSize
            )
        } else {
            Component.translatable(
                "screen.cobblemon_loot_menu.count",
                remaining,
                selected.size
            )
        }
        drawCenteredScaledText(
            graphics = graphics,
            text = text,
            centerX = panelX + 71,
            y = panelY + 132,
            maxWidth = 116,
            color = 0xC6C6C6
        )
    }

    private fun renderDetail(graphics: GuiGraphics, hoveredIndex: Int?) {
        val detailIndex = hoveredIndex
            ?: selected.firstOrNull { it in stacks.indices && !taken[it] }
            ?: stacks.indices.firstOrNull { !taken[it] }

        blit(graphics, LootTextures.DIVIDER, panelX + 148, panelY + 43, 112, 2)
        blit(graphics, LootTextures.DIVIDER, panelX + 148, panelY + 107, 112, 2)

        if (detailIndex == null) {
            blit(graphics, LootTextures.ICON_NONE, panelX + 198, panelY + 67, 12, 12)
            drawCenteredScaledText(
                graphics,
                Component.translatable("screen.cobblemon_loot_menu.no_loot"),
                panelX + 204,
                panelY + 114,
                116,
                0xC6C6C6
            )
            return
        }

        val stack = stacks[detailIndex]
        val rawName = stack.hoverName.string
        val fittedName = if (font.width(rawName) <= 116) {
            rawName
        } else {
            font.plainSubstrByWidth(rawName, 108) + "…"
        }

        graphics.drawCenteredString(
            font,
            fittedName,
            panelX + 204,
            panelY + 34,
            0xFFFFFF
        )

        blit(graphics, LootTextures.ITEM_FRAME, panelX + 180, panelY + 52, 48, 48)

        graphics.pose().pushPose()
        graphics.pose().translate((panelX + 204).toDouble(), (panelY + 72).toDouble(), 0.0)
        graphics.pose().scale(1.4f, 1.4f, 1f)
        graphics.renderItem(stack, -8, -8)
        graphics.pose().popPose()

        blit(graphics, LootTextures.QUANTITY_BADGE, panelX + 197, panelY + 86, 28, 12)
        graphics.drawCenteredString(
            font,
            "×${stack.count}",
            panelX + 211,
            panelY + 88,
            0xFFFFFF
        )

        drawCenteredScaledText(
            graphics,
            Component.translatable("screen.cobblemon_loot_menu.click_hint"),
            panelX + 204,
            panelY + 114,
            120,
            0xC6C6C6
        )
        drawCenteredScaledText(
            graphics,
            Component.translatable("screen.cobblemon_loot_menu.take_one_hint"),
            panelX + 204,
            panelY + 125,
            120,
            0x8D8D8D
        )
    }

    private fun renderScrollbar(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val maxScroll = maxScrollRows()
        if (maxScroll <= 0) return

        val trackX = panelX + 132
        val trackY = panelY + 32
        val gripY = scrollbarGripY()
        val gripHovered = isInside(mouseX, mouseY, trackX, gripY, 6, 22)

        blit(graphics, LootTextures.SCROLL_TRACK, trackX, trackY, 6, 94)
        blit(
            graphics,
            if (gripHovered) LootTextures.SCROLL_GRIP_HOVER else LootTextures.SCROLL_GRIP,
            trackX,
            gripY,
            6,
            22
        )
    }

    private fun renderButtons(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val canAct = taken.any { !it } && !requestInFlight
        val canTakeSelected = selected.isNotEmpty() && !requestInFlight

        drawButton(
            graphics,
            mouseX,
            mouseY,
            x = panelX + 7,
            label = Component.translatable("screen.cobblemon_loot_menu.drop_all"),
            kind = ButtonKind.DANGER,
            enabled = !requestInFlight
        )
        drawButton(
            graphics,
            mouseX,
            mouseY,
            x = panelX + 73,
            label = Component.translatable("screen.cobblemon_loot_menu.discard_all"),
            kind = ButtonKind.DANGER,
            enabled = !requestInFlight
        )
        drawButton(
            graphics,
            mouseX,
            mouseY,
            x = panelX + 139,
            label = Component.translatable("screen.cobblemon_loot_menu.take_all"),
            kind = ButtonKind.ACCEPT,
            enabled = canAct
        )
        drawButton(
            graphics,
            mouseX,
            mouseY,
            x = panelX + 205,
            label = Component.translatable("screen.cobblemon_loot_menu.take_selected"),
            kind = ButtonKind.ACCEPT,
            enabled = canTakeSelected
        )
    }

    private fun drawButton(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        label: Component,
        kind: ButtonKind,
        enabled: Boolean
    ) {
        val y = panelY + 152
        val hovered = enabled && isInside(mouseX, mouseY, x, y, 64, 20)
        val texture = when {
            !enabled -> LootTextures.BUTTON_DISABLED
            kind == ButtonKind.DANGER && hovered -> LootTextures.BUTTON_DANGER_HOVER
            kind == ButtonKind.DANGER -> LootTextures.BUTTON_DANGER
            kind == ButtonKind.ACCEPT && hovered -> LootTextures.BUTTON_ACCEPT_HOVER
            kind == ButtonKind.ACCEPT -> LootTextures.BUTTON_ACCEPT
            hovered -> LootTextures.BUTTON_NEUTRAL_HOVER
            else -> LootTextures.BUTTON_NEUTRAL
        }

        blit(graphics, texture, x, y, 64, 20)
        val textColor = when {
            !enabled -> 0x676767
            kind == ButtonKind.NEUTRAL -> 0x2F2F2F
            else -> 0xFFFFFF
        }

        drawCenteredScaledText(
            graphics,
            label,
            x + 32,
            y + 6,
            58,
            textColor
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (requestInFlight) return true

        if (isInside(mouseX, mouseY, panelX + 254, panelY + 9, 9, 9)) {
            requestDropAllAndClose()
            return true
        }

        if (maxScrollRows() > 0 && isInside(mouseX, mouseY, panelX + 132, panelY + 32, 6, 94)) {
            val ratio = ((mouseY - (panelY + 32) - 11.0) / (94.0 - 22.0))
                .coerceIn(0.0, 1.0)
            scrollRow = (ratio * maxScrollRows()).roundToInt()
            return true
        }

        val slotIndex = slotAt(mouseX, mouseY)
        if (slotIndex != null && slotIndex in stacks.indices && !taken[slotIndex]) {
            if (
                button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ||
                (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown())
            ) {
                sendAction(LootAction.TAKE_ONE, intArrayOf(slotIndex))
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (!selected.add(slotIndex)) selected.remove(slotIndex)
            }
            return true
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            when {
                isInside(mouseX, mouseY, panelX + 7, panelY + 152, 64, 20) -> {
                    requestDropAllAndClose()
                    return true
                }

                isInside(mouseX, mouseY, panelX + 73, panelY + 152, 64, 20) -> {
                    requestDiscardAllAndClose()
                    return true
                }

                isInside(mouseX, mouseY, panelX + 139, panelY + 152, 64, 20) && taken.any { !it } -> {
                    sendAction(LootAction.TAKE_ALL)
                    return true
                }

                isInside(mouseX, mouseY, panelX + 205, panelY + 152, 64, 20) && selected.isNotEmpty() -> {
                    sendAction(LootAction.TAKE_SELECTED, selected.toIntArray())
                    return true
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double
    ): Boolean {
        val maxScroll = maxScrollRows()
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

        if (isInside(mouseX, mouseY, panelX + 8, panelY + 28, 130, 118)) {
            val direction = when {
                scrollY > 0.0 -> -1
                scrollY < 0.0 -> 1
                else -> 0
            }
            scrollRow = Mth.clamp(scrollRow + direction, 0, maxScroll)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestDropAllAndClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        if (!resolvedByServer) {
            requestDropAllAndClose()
        } else {
            minecraft?.setScreen(null)
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun sendAction(action: LootAction, indices: IntArray = intArrayOf()) {
        if (requestInFlight) return
        requestInFlight = true
        ClientPlayNetworking.send(LootActionPayload(sessionId, action, indices))
    }

    private fun requestDropAllAndClose() {
        if (!resolvedByServer && !requestInFlight) {
            ClientPlayNetworking.send(
                LootActionPayload(sessionId, LootAction.DROP_ALL)
            )
        }
        resolvedByServer = true
        minecraft?.setScreen(null)
    }

    private fun requestDiscardAllAndClose() {
        if (!resolvedByServer && !requestInFlight) {
            ClientPlayNetworking.send(
                LootActionPayload(sessionId, LootAction.DISCARD_ALL)
            )
        }
        resolvedByServer = true
        minecraft?.setScreen(null)
    }

    private fun slotAt(mouseX: Double, mouseY: Double): Int? {
        val localX = mouseX - (panelX + 12)
        val localY = mouseY - (panelY + 32)
        if (localX < 0 || localY < 0) return null

        val column = (localX / 24).toInt()
        val row = (localY / 24).toInt()
        if (column !in 0 until COLUMNS || row !in 0 until VISIBLE_ROWS) return null

        val insideSlotX = localX - column * 24
        val insideSlotY = localY - row * 24
        if (insideSlotX >= 22 || insideSlotY >= 22) return null

        return scrollRow * COLUMNS + row * COLUMNS + column
    }

    private fun maxScrollRows(): Int {
        val totalRows = ceil(stacks.size / COLUMNS.toDouble()).toInt()
        return (totalRows - VISIBLE_ROWS).coerceAtLeast(0)
    }

    private fun clampScroll() {
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRows())
    }

    private fun scrollbarGripY(): Int {
        val maxScroll = maxScrollRows()
        if (maxScroll == 0) return panelY + 32
        val travel = 94 - 22
        return panelY + 32 + (scrollRow.toFloat() / maxScroll * travel).roundToInt()
    }

    private fun blit(
        graphics: GuiGraphics,
        texture: ResourceLocation,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        graphics.blit(texture, x, y, 0f, 0f, width, height, width, height)
    }

    private fun drawCenteredScaledText(
        graphics: GuiGraphics,
        text: Component,
        centerX: Int,
        y: Int,
        maxWidth: Int,
        color: Int
    ) {
        val textWidth = font.width(text).coerceAtLeast(1)
        val scale = (maxWidth.toFloat() / textWidth).coerceAtMost(1f)

        graphics.pose().pushPose()
        graphics.pose().translate(centerX.toDouble(), y.toDouble(), 0.0)
        graphics.pose().scale(scale, scale, 1f)
        graphics.drawString(
            font,
            text,
            -textWidth / 2,
            0,
            color,
            kindShadow(color)
        )
        graphics.pose().popPose()
    }

    private fun kindShadow(color: Int): Boolean = color == 0xFFFFFF

    private fun isInside(
        mouseX: Number,
        mouseY: Number,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        return mx >= x && mx < x + width && my >= y && my < y + height
    }

    private enum class ButtonKind {
        NEUTRAL,
        ACCEPT,
        DANGER
    }

    companion object {
        private const val PANEL_WIDTH = 276
        private const val PANEL_HEIGHT = 180
        private const val COLUMNS = 5
        private const val VISIBLE_ROWS = 4
        private const val VISIBLE_CELLS = COLUMNS * VISIBLE_ROWS
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // skip vanilla blur, the screen already draws its own overlay
    }
}
