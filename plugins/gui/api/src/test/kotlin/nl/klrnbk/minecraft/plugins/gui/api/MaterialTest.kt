package nl.klrnbk.minecraft.plugins.gui.api

import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MaterialTest {
    @Test
    fun `bare ids default to the minecraft namespace`() {
        assertEquals(Material.DIAMOND, Material.of("diamond"))
        assertEquals("minecraft:diamond", Material.DIAMOND.toString())
    }

    @Test
    fun `namespaced ids are kept`() {
        val custom = Material.of("klrnbk:token")
        assertEquals("klrnbk", custom.key.namespace())
        assertNotEquals(Material.DIAMOND, custom)
    }

    @Test
    fun `invalid ids are rejected`() {
        assertThrows<Exception> { Material.of("Not Valid!") }
    }
}
