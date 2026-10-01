package nl.klrnbk.minecraft.plugins.gui.common.click

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DragTrackerTest {
    @Test
    fun `collects gui and inventory slots between start and end`() {
        val tracker = DragTracker(guiSize = 27)
        tracker.start(GuiClickType.DRAG_LEFT)
        tracker.add(3)
        tracker.add(4)
        tracker.add(27) // first inventory slot
        tracker.add(62) // last inventory slot (hotbar 9)
        tracker.add(63) // invalid -> ignored
        tracker.add(4) // duplicate -> ignored
        val drag = tracker.end()!!
        assertEquals(GuiClickType.DRAG_LEFT, drag.type)
        assertEquals(setOf(3, 4), drag.guiSlots)
        assertEquals(setOf(0, 35), drag.inventorySlots)
    }

    @Test
    fun `end without start is ignored and end resets`() {
        val tracker = DragTracker(9)
        assertNull(tracker.end())
        tracker.start(GuiClickType.DRAG_RIGHT)
        tracker.add(1)
        tracker.end()
        assertNull(tracker.end())
    }

    @Test
    fun `slots added before a start are ignored and a new start clears old slots`() {
        val tracker = DragTracker(9)
        tracker.add(1)
        tracker.start(GuiClickType.DRAG_LEFT)
        tracker.add(2)
        tracker.start(GuiClickType.DRAG_RIGHT)
        tracker.add(3)
        val drag = tracker.end()!!
        assertEquals(GuiClickType.DRAG_RIGHT, drag.type)
        assertEquals(setOf(3), drag.guiSlots)
    }

    @Test
    fun `reset abandons the drag`() {
        val tracker = DragTracker(9)
        tracker.start(GuiClickType.DRAG_LEFT)
        tracker.reset()
        assertNull(tracker.end())
    }
}
