package nl.klrnbk.minecraft.plugins.gui.common.click

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.SlotArea
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ClickInterpreterTest {
    private fun click(
        mode: RawClickMode,
        button: Int,
        slot: Int = 4,
    ) = RawClick(101, 0, slot, button, mode, null)

    private fun single(
        mode: RawClickMode,
        button: Int,
        slot: Int = 4,
        guiSize: Int = 27,
    ) = assertInstanceOf(ClickStep.Click::class.java, ClickInterpreter.interpret(click(mode, button, slot), guiSize))

    @ParameterizedTest
    @CsvSource("0,LEFT", "1,RIGHT", "2,UNKNOWN")
    fun `pickup buttons`(
        button: Int,
        expected: GuiClickType,
    ) = assertEquals(expected, single(RawClickMode.PICKUP, button).type)

    @ParameterizedTest
    @CsvSource("0,SHIFT_LEFT", "1,SHIFT_RIGHT", "5,UNKNOWN")
    fun `quick move buttons are shift clicks`(
        button: Int,
        expected: GuiClickType,
    ) = assertEquals(expected, single(RawClickMode.QUICK_MOVE, button).type)

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7, 8])
    fun `swap with buttons 0 to 8 is a number key and reports the hotbar slot`(button: Int) {
        val step = single(RawClickMode.SWAP, button)
        assertEquals(GuiClickType.NUMBER_KEY, step.type)
        assertEquals(button, step.hotbarSlot)
    }

    @Test
    fun `swap with button 40 is the offhand key`() {
        val step = single(RawClickMode.SWAP, 40)
        assertEquals(GuiClickType.OFFHAND_SWAP, step.type)
        assertNull(step.hotbarSlot)
    }

    @ParameterizedTest
    @ValueSource(ints = [9, 39, 41, -1])
    fun `swap with other buttons is unknown`(button: Int) = assertEquals(GuiClickType.UNKNOWN, single(RawClickMode.SWAP, button).type)

    @Test
    fun `clone is the middle click`() = assertEquals(GuiClickType.MIDDLE, single(RawClickMode.CLONE, 2).type)

    @ParameterizedTest
    @CsvSource("0,DROP", "1,CONTROL_DROP", "2,UNKNOWN")
    fun `throw buttons`(
        button: Int,
        expected: GuiClickType,
    ) = assertEquals(expected, single(RawClickMode.THROW, button).type)

    @Test
    fun `pickup all is the double click`() = assertEquals(GuiClickType.DOUBLE_CLICK, single(RawClickMode.PICKUP_ALL, 0).type)

    @Test
    fun `clicks outside the window`() {
        assertEquals(GuiClickType.OUTSIDE_LEFT, single(RawClickMode.PICKUP, 0, ClickInterpreter.OUTSIDE_SLOT).type)
        assertEquals(GuiClickType.OUTSIDE_RIGHT, single(RawClickMode.PICKUP, 1, ClickInterpreter.OUTSIDE_SLOT).type)
        assertEquals(SlotArea.OUTSIDE, single(RawClickMode.PICKUP, 0, ClickInterpreter.OUTSIDE_SLOT).area)
    }

    @Test
    fun `an unknown click mode is unknown`() {
        assertSame(ClickStep.Unknown, ClickInterpreter.interpret(click(RawClickMode.UNKNOWN, 0), 27))
    }

    @ParameterizedTest
    @CsvSource("0,GUI", "26,GUI", "27,PLAYER_INVENTORY", "62,PLAYER_INVENTORY", "63,OUTSIDE", "-1,OUTSIDE", "-999,OUTSIDE", "500,OUTSIDE")
    fun `areas for a 27 slot gui`(
        slot: Int,
        expected: SlotArea,
    ) = assertEquals(expected, ClickInterpreter.areaOf(slot, 27))

    @Test
    fun `area boundaries follow the gui size`() {
        assertEquals(SlotArea.GUI, ClickInterpreter.areaOf(4, 5))
        assertEquals(SlotArea.PLAYER_INVENTORY, ClickInterpreter.areaOf(5, 5))
        assertEquals(SlotArea.PLAYER_INVENTORY, ClickInterpreter.areaOf(40, 5))
        assertEquals(SlotArea.OUTSIDE, ClickInterpreter.areaOf(41, 5))
    }

    @Test
    fun `slot area is attached to ordinary clicks`() {
        assertEquals(SlotArea.PLAYER_INVENTORY, single(RawClickMode.QUICK_MOVE, 0, 30).area)
        assertEquals(SlotArea.GUI, single(RawClickMode.QUICK_MOVE, 0, 3).area)
    }

    // Drag = QUICK_CRAFT. button = stage | (type << 2): stage 0 start, 1 add, 2 end; type 0 left, 1 right, 2 middle.
    @ParameterizedTest
    @CsvSource("0,DRAG_LEFT", "4,DRAG_RIGHT", "8,DRAG_MIDDLE")
    fun `drag start encodes the drag type in the button`(
        button: Int,
        expected: GuiClickType,
    ) {
        val step = ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, button, ClickInterpreter.OUTSIDE_SLOT), 27)
        assertEquals(expected, assertInstanceOf(ClickStep.DragStart::class.java, step).type)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 5, 9])
    fun `drag add carries the slot`(button: Int) {
        val step = ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, button, 7), 27)
        assertEquals(7, assertInstanceOf(ClickStep.DragAdd::class.java, step).slot)
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 6, 10])
    fun `drag end`(button: Int) {
        val step = ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, button, ClickInterpreter.OUTSIDE_SLOT), 27)
        assertSame(ClickStep.DragEnd, step)
    }

    @Test
    fun `malformed drag packets are unknown`() {
        assertSame(ClickStep.Unknown, ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, 3, 4), 27)) // stage 3
        assertSame(ClickStep.Unknown, ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, 12, 4), 27)) // type 3
        assertSame(ClickStep.Unknown, ClickInterpreter.interpret(click(RawClickMode.QUICK_CRAFT, 1, ClickInterpreter.OUTSIDE_SLOT), 27)) // add outside
    }
}
