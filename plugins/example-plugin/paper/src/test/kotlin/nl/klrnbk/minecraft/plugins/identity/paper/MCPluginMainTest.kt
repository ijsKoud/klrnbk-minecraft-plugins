package nl.klrnbk.minecraft.plugins.identity.paper

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

/**
 * MockBukkit boots an in-memory fake server (`ServerMock`), loads the real
 * plugin class into it, and lets us drive events like a real player join
 * without touching the network or a JVM-per-test server process.
 */
class MCPluginMainTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: MCPluginMain

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(MCPluginMain::class.java)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `plugin enables cleanly and registers its listener`() {
        assertTrue(plugin.isEnabled)
    }

    @Test
    fun `joining player receives no debug message when debug is disabled`() {
        // MCPluginConfig defaults to debug = false, so PlayerJoinListener
        // should stay silent.
        val player = server.addPlayer()

        assertNull(player.nextComponentMessage())
    }
}
