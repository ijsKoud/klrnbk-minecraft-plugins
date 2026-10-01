package nl.klrnbk.minecraft.plugins.gui.common.click

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType

/** The finished result of a drag. */
class CompletedDrag(
    val type: GuiClickType,
    val guiSlots: Set<Int>,
    val inventorySlots: Set<Int>,
)

/**
 * Collects the start / add / end packets of one drag into a [CompletedDrag].
 *
 * Not thread-safe on purpose: it is only touched from the owning viewer's serial executor, where
 * events are already ordered.
 */
class DragTracker(
    private val guiSize: Int,
) {
    private var type: GuiClickType? = null
    private val guiSlots = linkedSetOf<Int>()
    private val inventorySlots = linkedSetOf<Int>()

    fun start(type: GuiClickType) {
        this.type = type
        guiSlots.clear()
        inventorySlots.clear()
    }

    fun add(slot: Int) {
        if (type == null) return
        when {
            slot in 0 until guiSize -> guiSlots += slot
            slot in guiSize until guiSize + ClickInterpreter.PLAYER_SLOTS -> inventorySlots += slot - guiSize
        }
    }

    /** Ends the drag; `null` if no drag was in progress (a stray end packet). */
    fun end(): CompletedDrag? {
        val t = type ?: return null
        val result = CompletedDrag(t, guiSlots.toSet(), inventorySlots.toSet())
        reset()
        return result
    }

    fun reset() {
        type = null
        guiSlots.clear()
        inventorySlots.clear()
    }
}
