package nl.klrnbk.minecraft.plugins.gui.common.click

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.SlotArea
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode

/** One decoded step of the click protocol. */
sealed interface ClickStep {
    /** An ordinary click. */
    class Click(
        val type: GuiClickType,
        val area: SlotArea,
        val slot: Int,
        val hotbarSlot: Int?,
    ) : ClickStep

    /** The player pressed a mouse button to start a drag. */
    class DragStart(
        val type: GuiClickType,
    ) : ClickStep

    /** The dragged cursor entered [slot]. */
    class DragAdd(
        val slot: Int,
    ) : ClickStep

    /** The player released the button: the drag is complete. */
    data object DragEnd : ClickStep

    /** A packet we cannot make sense of. It is still answered with a resync. */
    data object Unknown : ClickStep
}

/**
 * Turns raw container clicks into [ClickStep]s.
 *
 * Minecraft 26.x still uses the same seven click modes as 1.5 did (`PICKUP`, `QUICK_MOVE`,
 * `SWAP`, `CLONE`, `THROW`, `QUICK_CRAFT`, `PICKUP_ALL`); what changed over the years is the
 * *encoding* of the packet (hashed stacks, container-id width), which the protocol layer
 * absorbs. Reference for the modes and buttons: Minecraft wiki, "Java Edition protocol/Packets",
 * *Click Container*.
 *
 * Pure and stateless, so it is exhaustively unit-tested.
 */
object ClickInterpreter {
    /** Slot value the client sends for "outside the window". */
    const val OUTSIDE_SLOT = -999

    /** Number of player-inventory slots shown below every container: 27 main + 9 hotbar. */
    const val PLAYER_SLOTS = 36

    fun interpret(
        click: RawClick,
        guiSize: Int,
    ): ClickStep {
        val area = areaOf(click.slot, guiSize)
        if (click.mode == RawClickMode.QUICK_CRAFT) return interpretDrag(click, area)

        val type: GuiClickType
        var hotbar: Int? = null
        when (click.mode) {
            RawClickMode.PICKUP ->
                type =
                    when {
                        area == SlotArea.OUTSIDE && click.button == 0 -> GuiClickType.OUTSIDE_LEFT
                        area == SlotArea.OUTSIDE && click.button == 1 -> GuiClickType.OUTSIDE_RIGHT
                        click.button == 0 -> GuiClickType.LEFT
                        click.button == 1 -> GuiClickType.RIGHT
                        else -> GuiClickType.UNKNOWN
                    }
            RawClickMode.QUICK_MOVE ->
                type =
                    when (click.button) {
                        0 -> GuiClickType.SHIFT_LEFT
                        1 -> GuiClickType.SHIFT_RIGHT
                        else -> GuiClickType.UNKNOWN
                    }
            RawClickMode.SWAP ->
                type =
                    when (click.button) {
                        in 0..8 -> GuiClickType.NUMBER_KEY.also { hotbar = click.button }
                        OFFHAND_BUTTON -> GuiClickType.OFFHAND_SWAP
                        else -> GuiClickType.UNKNOWN
                    }
            RawClickMode.CLONE -> type = GuiClickType.MIDDLE
            RawClickMode.THROW ->
                type =
                    when (click.button) {
                        0 -> GuiClickType.DROP
                        1 -> GuiClickType.CONTROL_DROP
                        else -> GuiClickType.UNKNOWN
                    }
            RawClickMode.PICKUP_ALL -> type = GuiClickType.DOUBLE_CLICK
            else -> return ClickStep.Unknown
        }
        if (area == SlotArea.OUTSIDE && type != GuiClickType.OUTSIDE_LEFT && type != GuiClickType.OUTSIDE_RIGHT) {
            // e.g. a drop/pickup-all on an invalid slot: still a click, just not on anything.
            return ClickStep.Click(type, SlotArea.OUTSIDE, click.slot, hotbar)
        }
        return ClickStep.Click(type, area, click.slot, hotbar)
    }

    /** Which part of the screen [slot] lies in, given a GUI of [guiSize] slots. */
    fun areaOf(
        slot: Int,
        guiSize: Int,
    ): SlotArea =
        when {
            slot in 0 until guiSize -> SlotArea.GUI
            slot in guiSize until guiSize + PLAYER_SLOTS -> SlotArea.PLAYER_INVENTORY
            else -> SlotArea.OUTSIDE
        }

    /**
     * `QUICK_CRAFT` packs three things into `button`: bits 0-1 are the stage (0 = start, 1 = add a
     * slot, 2 = end) and bits 2-3 the drag type (0 = left, 1 = right, 2 = middle/creative).
     */
    private fun interpretDrag(
        click: RawClick,
        area: SlotArea,
    ): ClickStep {
        val stage = click.button and 0b11
        val kind = (click.button shr 2) and 0b11
        val type =
            when (kind) {
                0 -> GuiClickType.DRAG_LEFT
                1 -> GuiClickType.DRAG_RIGHT
                2 -> GuiClickType.DRAG_MIDDLE
                else -> return ClickStep.Unknown
            }
        return when (stage) {
            0 -> ClickStep.DragStart(type)
            1 -> if (area == SlotArea.OUTSIDE) ClickStep.Unknown else ClickStep.DragAdd(click.slot)
            2 -> ClickStep.DragEnd
            else -> ClickStep.Unknown
        }
    }

    private const val OFFHAND_BUTTON = 40
}
