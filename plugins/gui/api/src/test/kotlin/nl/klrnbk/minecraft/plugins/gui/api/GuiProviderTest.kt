package nl.klrnbk.minecraft.plugins.gui.api

import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(InternalGuiApi::class)
class GuiProviderTest {
    @AfterEach
    fun cleanup() = GuiProvider.unregister()

    @Test
    fun `get fails with a helpful message before registration`() {
        val error = assertThrows<IllegalStateException> { GuiProvider.get() }
        assert("PacketEvents" in error.message!!)
        assertNull(GuiProvider.getOrNull())
    }

    @Test
    fun `register and unregister`() {
        val api = mockk<GuiApi>()
        GuiProvider.register(api)
        assertSame(api, GuiProvider.get())
        GuiProvider.unregister()
        assertThrows<IllegalStateException> { GuiProvider.get() }
    }
}
