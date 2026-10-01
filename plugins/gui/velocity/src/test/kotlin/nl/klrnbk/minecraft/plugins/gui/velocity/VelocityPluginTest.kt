package nl.klrnbk.minecraft.plugins.gui.velocity

import com.github.retrooper.packetevents.PacketEvents
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.gui.api.GuiProvider
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents.PeTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.util.Optional

/** Velocity has no plugin test harness (see the other KLRNBK plugins), so the proxy is a MockK mock and everything else is real. */
class VelocityPluginTest {
    private val server = mockk<ProxyServer>(relaxed = true)
    private val dataDirectory: java.nio.file.Path = java.nio.file.Files.createTempDirectory("klrnbk-gui")
    private val commands = mockk<com.velocitypowered.api.command.CommandManager>(relaxed = true)
    private val plugin = VelocityPlugin(NOPLogger.NOP_LOGGER, server, dataDirectory)

    init {
        io.mockk.every { server.commandManager } returns commands
    }

    @BeforeEach
    fun installPacketEvents() {
        PeTestSupport.install()
        PeTestSupport.api.init()
    }

    @AfterEach
    fun cleanup() {
        GuiProvider.unregister()
        dataDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `plugin can be constructed`() {
        assertTrue(plugin.javaClass.isAnnotationPresent(com.velocitypowered.api.plugin.Plugin::class.java))
    }

    @Test
    fun `the plugin declares the dependencies the framework needs`() {
        val deps = plugin.javaClass.getAnnotation(com.velocitypowered.api.plugin.Plugin::class.java).dependencies.associate { it.id to it.optional }
        assertEquals(false, deps["packetevents"], "packetevents is required")
        assertEquals(false, deps["klrnbk-runtime-velocity"], "the shared runtime is required")
        assertEquals(true, deps["floodgate"])
        assertEquals(true, deps["geyser"])
    }

    @Test
    fun `initialising registers the api and shutting down unregisters it`() {
        plugin.onProxyInitialization(ProxyInitializeEvent())
        val api = GuiProvider.get()
        assertTrue("PacketEvents" in api.protocolDescription)

        plugin.onProxyShutdown(ProxyShutdownEvent())
        assertNull(GuiProvider.getOrNull())
    }

    @Test
    fun `without an initialised PacketEvents the plugin fails with an actionable message`() {
        val previous = PacketEvents.getAPI()
        try {
            PacketEvents.setAPI(null)
            val error = assertThrows(IllegalStateException::class.java) { plugin.onProxyInitialization(ProxyInitializeEvent()) }
            assertTrue("PacketEvents" in error.message!! && "Velocity" in error.message!!)
            assertNull(GuiProvider.getOrNull())
        } finally {
            PacketEvents.setAPI(previous)
        }
    }

    @Test
    fun `player lifecycle events are safe before and after initialisation`() {
        val player = testPlayer()
        val other = mockk<RegisteredServer>(relaxed = true)
        // before initialisation: nothing to clean up, nothing may throw
        plugin.onDisconnect(DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN))
        plugin.onServerConnected(ServerConnectedEvent(player, other, other))
        plugin.onServerPostConnect(ServerPostConnectEvent(player, other))

        plugin.onProxyInitialization(ProxyInitializeEvent())
        val gui = GuiProvider.get().create("x", 1)
        plugin.onServerConnected(ServerConnectedEvent(player, other, null)) // first login: no previous server
        plugin.onDisconnect(DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN))
        assertTrue(gui.viewers.isEmpty())
        plugin.onProxyShutdown(ProxyShutdownEvent())
    }

    /** In registration order: the admin command first, then menu commands. */
    private fun registeredCommands(): List<com.velocitypowered.api.command.SimpleCommand> {
        val metas = mutableListOf<com.velocitypowered.api.command.CommandMeta>()
        val cmds = mutableListOf<com.velocitypowered.api.command.Command>()
        io.mockk.verify(atLeast = 0) { commands.register(capture(metas), capture(cmds)) }
        return cmds.map { it as com.velocitypowered.api.command.SimpleCommand }
    }

    @Test
    fun `first start seeds the example menu, loads it and registers the admin and menu commands`() {
        val metaBuilder = mockk<com.velocitypowered.api.command.CommandMeta.Builder>(relaxed = true)
        val names = mutableListOf<String>()
        io.mockk.every { commands.metaBuilder(any<String>()) } answers {
            names += firstArg<String>()
            metaBuilder
        }
        io.mockk.every { metaBuilder.aliases(*anyVararg()) } returns metaBuilder
        io.mockk.every { metaBuilder.plugin(any()) } returns metaBuilder

        plugin.onProxyInitialization(ProxyInitializeEvent())

        assertTrue(java.nio.file.Files.exists(dataDirectory.resolve("menus/example.yml")))
        assertEquals(setOf("example"), GuiProvider.get().menus.ids)
        assertEquals(listOf("klrnbkgui", "guiexample"), names)
        plugin.onProxyShutdown(ProxyShutdownEvent())
        io.mockk.verify { commands.unregister("guiexample") }
    }

    @Test
    fun `a broken menu file never stops the framework from starting`() {
        java.nio.file.Files.createDirectories(dataDirectory.resolve("menus"))
        java.nio.file.Files.writeString(dataDirectory.resolve("menus/broken.yml"), "title: [")
        java.nio.file.Files.writeString(dataDirectory.resolve("menus/fine.yml"), "title: x\nrows: 1")
        plugin.onProxyInitialization(ProxyInitializeEvent())
        assertEquals(setOf("fine"), GuiProvider.get().menus.ids)
    }

    @Test
    fun `the admin command reloads menus and reports problems`() {
        plugin.onProxyInitialization(ProxyInitializeEvent())
        val admin = registeredCommands().first()
        val sender = mockk<com.velocitypowered.api.command.CommandSource>(relaxed = true) { io.mockk.every { hasPermission("klrnbk.gui.admin") } returns true }
        java.nio.file.Files.writeString(dataDirectory.resolve("menus/new.yml"), "title: x\nrows: 1\nitems:\n  a: {slot: 0}")
        val invocation = mockk<com.velocitypowered.api.command.SimpleCommand.Invocation> {
            io.mockk.every { source() } returns sender
            io.mockk.every { arguments() } returns arrayOf("reload")
        }
        admin.execute(invocation)
        assertTrue("new" in GuiProvider.get().menus.ids)
        io.mockk.verify { sender.sendMessage(match<net.kyori.adventure.text.Component> { true }) }
        assertTrue(admin.suggest(mockk { io.mockk.every { arguments() } returns arrayOf("op") }).contains("open"))
    }

    @Test
    fun `players without the admin permission cannot reload`() {
        plugin.onProxyInitialization(ProxyInitializeEvent())
        val admin = registeredCommands().first()
        val sender = mockk<com.velocitypowered.api.command.CommandSource>(relaxed = true) { io.mockk.every { hasPermission(any()) } returns false }
        java.nio.file.Files.writeString(dataDirectory.resolve("menus/new.yml"), "title: x\nrows: 1")
        admin.execute(mockk { io.mockk.every { source() } returns sender; io.mockk.every { arguments() } returns arrayOf("reload") })
        assertTrue("new" !in GuiProvider.get().menus.ids)
    }
}

