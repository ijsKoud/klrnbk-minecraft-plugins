package nl.klrnbk.minecraft.plugins.identity.velocity

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.nio.file.Files

/**
 * Velocity has no dedicated test framework (nothing like MockBukkit), so
 * ProxyServer and Logger — the two things Velocity injects into the plugin
 * constructor — are mocked directly with MockK instead.
 */
class MCVelocityPluginTest {
    @Test
    fun `logs an initialization message on ProxyInitializeEvent`() {
        val server = mockk<ProxyServer>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val dataDirectory = Files.createTempDirectory("mcplugin-test")

        val plugin = MCVelocityPlugin(server, logger, dataDirectory)
        plugin.onProxyInitialize(ProxyInitializeEvent())

        verify { logger.info(match { it.contains("initialized") }) }
    }
}
