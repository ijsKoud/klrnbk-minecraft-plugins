package nl.klrnbk.minecraft.plugins.gui.example

import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.common.FakeGuiProtocol
import nl.klrnbk.minecraft.plugins.gui.common.actions.ProxyActionExecutor
import org.slf4j.helpers.NOPLogger
import nl.klrnbk.minecraft.plugins.gui.common.GuiTestHarness
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Runs the example against the real framework implementation with a fake protocol: the example must keep compiling and working against the final API. */
class ExampleMenuTest {
    private val proxy = mockk<ProxyServer>(relaxed = true)
    private val harness = GuiTestHarness(actionsFactory = { manager -> ProxyActionExecutor(proxy, manager, NOPLogger.NOP_LOGGER) })
    private val menu = ExampleMenu(harness.api, proxy)
    private val player = testPlayer("Alex")

    private fun open(): Int {
        menu.open(player)
        return harness.protocol.opsOf<FakeGuiProtocol.Op.Open>().last().window.containerId
    }

    @Test
    fun `the plugin depends on the gui framework`() {
        val plugin = ExamplePlugin::class.java.getAnnotation(com.velocitypowered.api.plugin.Plugin::class.java)
        assertNotNull(plugin)
        assertTrue(plugin.dependencies.any { it.id == "klrnbk-gui" && !it.optional })
    }

    @Test
    fun `the menu opens with its items and a filler`() {
        open()
        val window = harness.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window
        assertEquals(27, window.items.size)
        assertNotNull(window.items[10])
        assertNotNull(window.items[0], "empty slots are filled with the pane")
    }

    @Test
    fun `the spawn item runs a server command then closes`() {
        val recording = GuiTestHarness()
        val recordingMenu = ExampleMenu(recording.api, proxy)
        recordingMenu.open(player)
        val id = recording.protocol.opsOf<FakeGuiProtocol.Op.Open>().last().window.containerId
        recording.protocol.click(player, id, 10)
        assertEquals(
            listOf<GuiAction>(GuiAction.ExecuteServerCommand("spawn"), GuiAction.Close),
            recording.actions.executed.map { it.first },
        )
    }

    @Test
    fun `with real actions the spawn item sends the player's chat command and closes the menu`() {
        val id = open()
        harness.protocol.click(player, id, 10)
        io.mockk.verify { player.spoofChatInput("/spawn") }
        assertEquals(id, harness.protocol.opsOf<FakeGuiProtocol.Op.Close>().single().containerId)
    }

    @Test
    fun `the counter is per viewer state and the item re-renders on click`() {
        val id = open()
        harness.protocol.ops.clear()
        harness.protocol.click(player, id, 15) // left: +1
        harness.protocol.click(player, id, 15, 0, RawClickMode.QUICK_MOVE) // shift-left: +10
        val counterItem = harness.protocol.opsOf<FakeGuiProtocol.Op.Update>().last().changes[15]!!
        assertEquals("<green>Counter: 11", nl.klrnbk.minecraft.plugins.gui.api.item.GuiItemBuilder().also { it.nameComponent = counterItem.name }.name)
    }

    @Test
    fun `the click test item records number keys with their hotbar slot`() {
        val id = open()
        harness.protocol.click(player, id, 22, 4, RawClickMode.SWAP)
        val paper = harness.protocol.opsOf<FakeGuiProtocol.Op.Update>().last().changes[14]!!
        assertTrue(paper.lore.any { "NUMBER_KEY (hotbar 4)" in net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) })
    }

    @Test
    fun `opening reports failure to the player`() {
        harness.protocol.checkResult = nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck.UNSUPPORTED_CLIENT
        menu.open(player)
        io.mockk.verify { player.sendMessage(match<net.kyori.adventure.text.Component> { true }) }
        assertEquals(GuiOpenResult.UNSUPPORTED_CLIENT, harness.api.create("x", 1).open(player))
    }
}
