package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.player.ClientVersion
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the `minecraft:menu` registry ids. If this fails after a Minecraft upgrade, Mojang changed the registry:
 * verify the new order (see IMPLEMENTATION.md, "Upgrading") and update [MenuTypes] — do not just edit the expectation.
 */
class MenuTypesTest {
    @Test
    fun `generic 9xN chests are ids 0 to 5`() {
        (1..6).forEach { rows -> assertEquals(rows - 1, MenuTypes.idFor(GuiLayout.chest(rows))) }
    }

    @Test
    fun `dispenser is generic_3x3 and hopper is hopper`() {
        assertEquals(6, MenuTypes.idFor(GuiLayout.Dispenser))
        assertEquals(16, MenuTypes.idFor(GuiLayout.Hopper))
    }

    @Test
    fun `clients older than 1_21_5 are refused, newer ones accepted`() {
        assertFalse(MenuTypes.isSupported(ClientVersion.V_1_21_4))
        assertFalse(MenuTypes.isSupported(ClientVersion.V_1_20_5))
        assertTrue(MenuTypes.isSupported(ClientVersion.V_1_21_5))
        assertTrue(MenuTypes.isSupported(ClientVersion.V_26_2))
        assertTrue(MenuTypes.isSupported(ClientVersion.V_26_3))
    }
}
