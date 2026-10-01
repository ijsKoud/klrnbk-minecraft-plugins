package nl.klrnbk.minecraft.plugins.gui.api

import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GuiActionTest {
    @Test
    fun `a leading slash is stripped from every command action`() {
        assertEquals("spawn", GuiAction.ExecutePlayerCommand("/spawn").command)
        assertEquals("spawn", GuiAction.ExecuteServerCommand("spawn").command)
        assertEquals("glist", GuiAction.ExecuteProxyCommand("/glist").command)
        assertEquals("home set", GuiAction.ExecutePlayerCommand("  /home set  ").command)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "/", "  /  "])
    fun `blank commands are rejected`(command: String) {
        assertThrows<IllegalArgumentException> { GuiAction.ExecutePlayerCommand(command) }
        assertThrows<IllegalArgumentException> { GuiAction.ExecuteServerCommand(command) }
        assertThrows<IllegalArgumentException> { GuiAction.ExecuteProxyCommand(command) }
    }

    @Test
    fun `commands with control characters are rejected so one action cannot smuggle a second command`() {
        assertThrows<IllegalArgumentException> { GuiAction.ExecutePlayerCommand("spawn\nop me") }
        assertThrows<IllegalArgumentException> { GuiAction.ExecuteServerCommand("spawn\u0000") }
        assertThrows<IllegalArgumentException> { GuiAction.ExecuteServerCommand("a\rb") }
    }

    @Test
    fun `overlong commands are rejected`() {
        GuiAction.ExecutePlayerCommand("a".repeat(255))
        assertThrows<IllegalArgumentException> { GuiAction.ExecutePlayerCommand("a".repeat(256)) }
    }

    @Test
    fun `proxy commands default to the player and can run as console`() {
        assertEquals(ProxyCommandExecutor.PLAYER, GuiAction.ExecuteProxyCommand("glist").executor)
        assertEquals(ProxyCommandExecutor.CONSOLE, GuiAction.ExecuteProxyCommand("glist", ProxyCommandExecutor.CONSOLE).executor)
        assertNotEquals(GuiAction.ExecuteProxyCommand("glist"), GuiAction.ExecuteProxyCommand("glist", ProxyCommandExecutor.CONSOLE))
    }

    @Test
    fun `server commands may name a target server but not a blank one`() {
        assertNull(GuiAction.ExecuteServerCommand("spawn").server)
        assertEquals("lobby", GuiAction.ExecuteServerCommand("spawn", "lobby").server)
        assertThrows<IllegalArgumentException> { GuiAction.ExecuteServerCommand("spawn", " ") }
    }

    @Test
    fun `the three command kinds are distinct even for the same text`() {
        assertNotEquals(GuiAction.ExecutePlayerCommand("spawn") as Any, GuiAction.ExecuteServerCommand("spawn") as Any)
        assertNotEquals(GuiAction.ExecuteServerCommand("spawn") as Any, GuiAction.ExecuteProxyCommand("spawn") as Any)
    }

    @Test
    fun `connect validates the server name`() {
        assertEquals("lobby", GuiAction.ConnectToServer("lobby").server)
        assertThrows<IllegalArgumentException> { GuiAction.ConnectToServer("") }
    }

    @Test
    fun `plugin message channels must be namespaced and data is defensively copied`() {
        val data = byteArrayOf(1, 2, 3)
        val action = GuiAction.SendPluginMessage("klrnbk:gui", data)
        data[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), action.data)
        action.data[1] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), action.data)
        assertThrows<IllegalArgumentException> { GuiAction.SendPluginMessage("nonamespace", byteArrayOf()) }
        assertThrows<IllegalArgumentException> { GuiAction.SendPluginMessage("Bad:Channel", byteArrayOf()) }
    }

    @Test
    fun `client actions are clickable messages, never silent execution`() {
        val action = GuiAction.SendClickableMessage("<green>[Click]", ClientClick.SuggestCommand("/warp"))
        assertEquals("warp", (action.click as ClientClick.SuggestCommand).command)
        assertThrows<IllegalArgumentException> { GuiAction.SendClickableMessage(" ", ClientClick.CopyToClipboard("x")) }
        assertThrows<IllegalArgumentException> { ClientClick.OpenUrl("javascript:alert(1)") }
        assertThrows<IllegalArgumentException> { ClientClick.RunCommand("") }
        ClientClick.OpenUrl("https://klrnbk.nl")
    }

    @Test
    fun `composite actions compare by content`() {
        val a = GuiAction.Composite(GuiAction.Close, GuiAction.ConnectToServer("lobby"))
        val b = GuiAction.Composite(listOf(GuiAction.Close, GuiAction.ConnectToServer("lobby")))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
