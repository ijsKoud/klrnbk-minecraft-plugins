package nl.klrnbk.minecraft.plugins.gui.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuiPlatformTest {
    @Test
    fun `java is expected to support every capability`() {
        GuiCapability.entries.forEach { assertTrue(GuiPlatform.JAVA.supports(it), "$it") }
    }

    @Test
    fun `bedrock supports the basics but not drag or number keys`() {
        assertTrue(GuiPlatform.BEDROCK.supports(GuiCapability.LEFT_RIGHT_CLICK))
        assertTrue(GuiPlatform.BEDROCK.supports(GuiCapability.RICH_TOOLTIP))
        assertFalse(GuiPlatform.BEDROCK.supports(GuiCapability.DRAG))
        assertFalse(GuiPlatform.BEDROCK.supports(GuiCapability.NUMBER_KEY_CLICK))
        assertFalse(GuiPlatform.BEDROCK.supports(GuiCapability.RAW_COMPONENTS))
    }
}
