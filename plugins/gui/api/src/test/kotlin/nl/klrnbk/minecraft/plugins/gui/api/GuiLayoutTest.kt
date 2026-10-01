package nl.klrnbk.minecraft.plugins.gui.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GuiLayoutTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6])
    fun `chest layouts of 1 to 6 rows have rows times nine slots`(rows: Int) {
        val layout = GuiLayout.chest(rows)
        assertEquals(rows * 9, layout.size)
        assertEquals(9, layout.columns)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 7, 100, Int.MIN_VALUE, Int.MAX_VALUE])
    fun `invalid chest sizes are rejected`(rows: Int) {
        assertThrows<IllegalArgumentException> { GuiLayout.chest(rows) }
    }

    @Test
    fun `hopper and dispenser have their vanilla sizes`() {
        assertEquals(5, GuiLayout.Hopper.size)
        assertEquals(9, GuiLayout.Dispenser.size)
        assertEquals(3, GuiLayout.Dispenser.columns)
    }

    @Test
    fun `chest layouts compare by rows`() {
        assertEquals(GuiLayout.chest(3), GuiLayout.chest(3))
        assertNotEquals(GuiLayout.chest(3), GuiLayout.chest(4))
        assertEquals(GuiLayout.chest(3).hashCode(), GuiLayout.chest(3).hashCode())
    }

    @Test
    fun `slot converts row and column to an index and validates bounds`() {
        val layout = GuiLayout.chest(3)
        assertEquals(0, layout.slot(0, 0))
        assertEquals(13, layout.slot(1, 4))
        assertEquals(26, layout.slot(2, 8))
        assertThrows<IllegalArgumentException> { layout.slot(3, 0) }
        assertThrows<IllegalArgumentException> { layout.slot(0, 9) }
        assertThrows<IllegalArgumentException> { layout.slot(-1, 0) }
        assertEquals(8, GuiLayout.Dispenser.slot(2, 2))
        assertThrows<IllegalArgumentException> { GuiLayout.Dispenser.slot(0, 3) }
    }
}
