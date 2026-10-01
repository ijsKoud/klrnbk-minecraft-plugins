package nl.klrnbk.minecraft.plugins.whitelist.velocity

import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.mockk
import io.mockk.verify
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files

/**
 * Velocity has no dedicated test framework (nothing like MockBukkit), so ProxyServer —
 * the only Velocity-owned thing the plugin needs — is mocked with MockK; everything else is real.
 */
class VelocityPluginTest {
    private val dataDirectory = Files.createTempDirectory("whitelist-velocity")
    private val server = mockk<ProxyServer>(relaxed = true)
    private val plugin = VelocityPlugin(server, NOPLogger.NOP_LOGGER, dataDirectory)

    @AfterEach
    fun cleanup() {
        WhitelistProvider.unregister()
        dataDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `plugin can be constructed`() {
        assertNotNull(plugin)
    }

    @Test
    fun `starting registers the api, the command and the listener`() {
        plugin.onProxyInitialization(ProxyInitializeEvent())

        val api = WhitelistProvider.get()
        assertFalse(api.isWhitelistEnabled())
        assertTrue(Files.exists(dataDirectory.resolve("config.yml")))

        verify { server.commandManager.register(any<CommandMeta>(), any<BrigadierCommand>()) }
        verify { server.eventManager.register(plugin, any<Any>()) }

        plugin.onProxyShutdownEvent(ProxyShutdownEvent())
    }

    @Test
    fun `shutting down unregisters the api`() {
        plugin.onProxyInitialization(ProxyInitializeEvent())
        plugin.onProxyShutdownEvent(ProxyShutdownEvent())

        assertThrows(IllegalStateException::class.java) { WhitelistProvider.get() }
    }
}
