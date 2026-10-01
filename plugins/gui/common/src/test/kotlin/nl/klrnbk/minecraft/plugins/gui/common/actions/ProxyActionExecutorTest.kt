package nl.klrnbk.minecraft.plugins.gui.common.actions

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import nl.klrnbk.minecraft.plugins.gui.common.GuiTestHarness
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.util.Optional
import java.util.concurrent.CompletableFuture

class ProxyActionExecutorTest {
    private val harness = GuiTestHarness()
    private val commands = mockk<com.velocitypowered.api.command.CommandManager>(relaxed = true)
    private val server = mockk<ProxyServer>(relaxed = true) { every { commandManager } returns commands }
    private val player: Player = testPlayer("Alex")
    private val executor = ProxyActionExecutor(server, harness.manager, NOPLogger.NOP_LOGGER, pendingTimeoutSeconds = 1)

    private fun registeredServer(name: String): RegisteredServer =
        mockk(relaxed = true) { every { serverInfo } returns ServerInfo(name, java.net.InetSocketAddress("127.0.0.1", 25565)) }

    private fun currentServer(name: String?) {
        every { player.currentServer } returns
            (
                if (name == null) {
                    Optional.empty()
                } else {
                    Optional.of(mockk<ServerConnection>(relaxed = true) { every { serverInfo } returns ServerInfo(name, java.net.InetSocketAddress("127.0.0.1", 1)) })
                }
            )
    }

    private fun connectionBuilder(success: Boolean = true): ConnectionRequestBuilder {
        val result = mockk<ConnectionRequestBuilder.Result>(relaxed = true) { every { isSuccessful } returns success }
        return mockk(relaxed = true) { every { connect() } returns CompletableFuture.completedFuture(result) }
    }

    // ---- player commands -----------------------------------------------------------------------

    @Test
    fun `a player command that the proxy knows runs on the proxy as the player`() {
        every { commands.hasCommand("glist", player) } returns true
        executor.execute(GuiAction.ExecutePlayerCommand("glist all"), player, null)
        verify { commands.executeAsync(player, "glist all") }
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `a player command the proxy does not know is forwarded to the backend as chat input`() {
        every { commands.hasCommand("spawn", player) } returns false
        executor.execute(GuiAction.ExecutePlayerCommand("spawn"), player, null)
        verify { player.spoofChatInput("/spawn") }
        verify(exactly = 0) { commands.executeAsync(any<CommandSource>(), any()) }
    }

    // ---- proxy commands ------------------------------------------------------------------------

    @Test
    fun `proxy commands never reach the backend`() {
        executor.execute(GuiAction.ExecuteProxyCommand("glist"), player, null)
        verify { commands.executeAsync(player, "glist") }
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `proxy commands can run as console`() {
        val console = mockk<com.velocitypowered.api.proxy.ConsoleCommandSource>()
        every { server.consoleCommandSource } returns console
        executor.execute(GuiAction.ExecuteProxyCommand("send Alex lobby", ProxyCommandExecutor.CONSOLE), player, null)
        verify { commands.executeAsync(console, "send Alex lobby") }
    }

    // ---- server commands -----------------------------------------------------------------------

    @Test
    fun `a server command bypasses the proxy and goes straight to the current backend`() {
        currentServer("survival")
        executor.execute(GuiAction.ExecuteServerCommand("spawn"), player, null)
        verify { player.spoofChatInput("/spawn") }
        verify(exactly = 0) { commands.executeAsync(any<CommandSource>(), any()) }
    }

    @Test
    fun `a server command naming the current server runs immediately`() {
        currentServer("Survival")
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        verify { player.spoofChatInput("/spawn") }
    }

    @Test
    fun `a server command for another server connects first and only runs after the post-connect event`() {
        currentServer("lobby")
        val target = registeredServer("survival")
        every { server.getServer("survival") } returns Optional.of(target)
        every { player.createConnectionRequest(target) } returns connectionBuilder()

        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        verify(exactly = 0) { player.spoofChatInput(any()) }

        currentServer("survival") // the connection completed
        executor.onServerConnected(player)
        verify(exactly = 1) { player.spoofChatInput("/spawn") }

        executor.onServerConnected(player) // consumed: not run twice
        verify(exactly = 1) { player.spoofChatInput("/spawn") }
    }

    @Test
    fun `a queued server command is dropped when the connection failed`() {
        currentServer("lobby")
        val target = registeredServer("survival")
        every { server.getServer("survival") } returns Optional.of(target)
        every { player.createConnectionRequest(target) } returns connectionBuilder(success = false)
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        currentServer("survival")
        executor.onServerConnected(player)
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `a queued server command is dropped when the player ends up on another server`() {
        currentServer("lobby")
        val target = registeredServer("survival")
        every { server.getServer("survival") } returns Optional.of(target)
        every { player.createConnectionRequest(target) } returns connectionBuilder()
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        currentServer("creative")
        executor.onServerConnected(player)
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `a queued server command expires`() {
        currentServer("lobby")
        val target = registeredServer("survival")
        every { server.getServer("survival") } returns Optional.of(target)
        every { player.createConnectionRequest(target) } returns connectionBuilder()
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        Thread.sleep(1200)
        currentServer("survival")
        executor.onServerConnected(player)
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `forget discards a queued command`() {
        currentServer("lobby")
        val target = registeredServer("survival")
        every { server.getServer("survival") } returns Optional.of(target)
        every { player.createConnectionRequest(target) } returns connectionBuilder()
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "survival"), player, null)
        executor.forget(player.uniqueId)
        currentServer("survival")
        executor.onServerConnected(player)
        verify(exactly = 0) { player.spoofChatInput(any()) }
    }

    @Test
    fun `a server command for an unknown server does nothing but log`() {
        currentServer("lobby")
        every { server.getServer("nowhere") } returns Optional.empty()
        executor.execute(GuiAction.ExecuteServerCommand("spawn", "nowhere"), player, null)
        verify(exactly = 0) { player.spoofChatInput(any()) }
        verify(exactly = 0) { player.createConnectionRequest(any()) }
    }

    // ---- connection / messages -----------------------------------------------------------------

    @Test
    fun `connect sends the player to a known server only`() {
        val target = registeredServer("lobby")
        val builder = connectionBuilder()
        every { server.getServer("lobby") } returns Optional.of(target)
        every { server.getServer("ghost") } returns Optional.empty()
        every { player.createConnectionRequest(target) } returns builder
        executor.execute(GuiAction.ConnectToServer("lobby"), player, null)
        executor.execute(GuiAction.ConnectToServer("ghost"), player, null)
        verify(exactly = 1) { builder.fireAndForget() }
        verify(exactly = 1) { player.createConnectionRequest(any()) }
    }

    @Test
    fun `plugin messages go to the backend connection not the client`() {
        val connection = mockk<ServerConnection>(relaxed = true)
        every { player.currentServer } returns Optional.of(connection)
        val id = slot<ChannelIdentifier>()
        val bytes = slot<ByteArray>()
        executor.execute(GuiAction.SendPluginMessage("klrnbk:gui", byteArrayOf(1, 2)), player, null)
        verify { connection.sendPluginMessage(capture(id), capture(bytes)) }
        assertEquals("klrnbk:gui", id.captured.id)
        assertArrayEquals(byteArrayOf(1, 2), bytes.captured)
        verify(exactly = 0) { player.sendPluginMessage(any<ChannelIdentifier>(), any<ByteArray>()) }
    }

    @Test
    fun `plugin messages without a backend are dropped`() {
        every { player.currentServer } returns Optional.empty()
        executor.execute(GuiAction.SendPluginMessage("klrnbk:gui", byteArrayOf()), player, null)
    }

    @Test
    fun `messages are parsed as MiniMessage`() {
        val sent = slot<Component>()
        every { player.sendMessage(capture(sent)) } returns Unit
        executor.execute(GuiAction.SendMessage("<green>Hello"), player, null)
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN, sent.captured.color())
    }

    @Test
    fun `client actions are clickable chat messages with the right click event`() {
        fun clickOf(click: ClientClick): ClickEvent<*> {
            val sent = slot<Component>()
            every { player.sendMessage(capture(sent)) } returns Unit
            executor.execute(GuiAction.SendClickableMessage("[go]", click), player, null)
            return sent.captured.clickEvent()!!
        }
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, clickOf(ClientClick.SuggestCommand("warp")).action())
        assertEquals(ClickEvent.Action.RUN_COMMAND, clickOf(ClientClick.RunCommand("warp")).action())
        assertEquals(ClickEvent.Action.OPEN_URL, clickOf(ClientClick.OpenUrl("https://klrnbk.nl")).action())
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, clickOf(ClientClick.CopyToClipboard("x")).action())
    }

    // ---- gui actions ---------------------------------------------------------------------------

    @Test
    fun `close and refresh act on the players current gui`() {
        val gui = harness.api.create("x", 1)
        gui.open(player)
        executor.execute(GuiAction.Refresh, player, null)
        executor.execute(GuiAction.Close, player, null)
        assertFalse(gui.isViewedBy(player))
        executor.execute(GuiAction.Close, player, null) // nothing open: harmless
    }

    @Test
    fun `open gui replaces the current gui`() {
        val first = harness.api.create("a", 1)
        val second = harness.api.create("b", 1)
        first.open(player)
        executor.execute(GuiAction.OpenGui { second }, player, null)
        assertTrue(second.isViewedBy(player))
        assertFalse(first.isViewedBy(player))
    }

    @Test
    fun `run needs an event and composite continues after a failing step`() {
        var ran = 0
        val gui = harness.api.create("x", 1).setItem(0, guiItem { material = Material.STONE })
        gui.open(player)
        executor.execute(GuiAction.Run { ran++ }, player, null) // no event: contained
        assertEquals(0, ran)

        executor.execute(
            GuiAction.Composite(
                GuiAction.Run { error("first fails") },
                GuiAction.Close,
            ),
            player,
            null,
        )
        assertFalse(gui.isViewedBy(player))
    }

    @Test
    fun `an exception inside an action never escapes the executor`() {
        every { player.spoofChatInput(any()) } throws IllegalStateException("no backend")
        currentServer(null)
        executor.execute(GuiAction.ExecuteServerCommand("spawn"), player, null)
    }
}
