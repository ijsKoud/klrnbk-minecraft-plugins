package nl.klrnbk.minecraft.plugins.gui.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuiClickTypeTest {
    @Test
    fun `primary clicks are the four plain gestures`() {
        assertEquals(setOf(GuiClickType.LEFT, GuiClickType.RIGHT, GuiClickType.SHIFT_LEFT, GuiClickType.SHIFT_RIGHT), GuiClickType.PRIMARY)
    }

    @Test
    fun `helpers classify click types`() {
        assertTrue(GuiClickType.SHIFT_LEFT.isShiftClick)
        assertTrue(GuiClickType.SHIFT_LEFT.isLeftClick)
        assertTrue(GuiClickType.RIGHT.isRightClick)
        assertFalse(GuiClickType.DROP.isLeftClick)
        assertTrue(GuiClickType.DRAG_MIDDLE.isDrag)
        assertFalse(GuiClickType.LEFT.isDrag)
    }
}
