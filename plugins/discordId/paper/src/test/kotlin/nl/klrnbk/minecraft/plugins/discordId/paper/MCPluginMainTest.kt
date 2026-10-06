package nl.klrnbk.minecraft.plugins.discordId.paper

import nl.klrnbk.minecraft.plugins.discordId.common.identityPlayer
import nl.klrnbk.minecraft.plugins.discordId.common.registerIdentity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * MockBukkit boots an in-memory fake server and loads the real plugin class into it, so nothing here touches
 * the network or a real server.
 */
class MCPluginMainTest {
    private val harness = PaperTestHarness()

    @AfterEach
    fun tearDown() = harness.close()

    @Test
    fun `the plugin enables and creates its default files in proxy mode`() {
        registerIdentity(identityPlayer("Alice"))

        val plugin = harness.load(useProxy = true)

        assertTrue(plugin.isEnabled)
        assertEquals(harness.dataFolder, plugin.dataFolder)
        assertTrue(File(plugin.dataFolder, "database.db").exists())
    }

    @Test
    fun `in proxy mode neither the bot nor the commands start on Paper`() {
        registerIdentity(identityPlayer("Alice"))

        harness.load(useProxy = true)

        assertNull(harness.server.commandMap.getCommand("discordid"))
        assertEquals(0, harness.botMain.starts)
    }

    @Test
    fun `without the Identity plugin the plugin fails to enable`() {
        // IdentityProvider is only registered by the Identity plugin, which is not in this server.
        assertThrows(Exception::class.java) { harness.load(useProxy = true) }
    }

    @Test
    fun `disabling stops cleanly`() {
        registerIdentity(identityPlayer("Alice"))
        val plugin = harness.load(useProxy = true)

        plugin.onDisable()

        // A second disable (e.g. after a failed enable) must not throw either.
        plugin.onDisable()
    }

    @Test
    fun `without proxy mode the bot starts and the discordid command is registered`() {
        registerIdentity(identityPlayer("Alice"))
        harness.load(useProxy = true)

        harness.startFully()

        assertEquals(1, harness.botMain.starts)
        assertNotNull(harness.server.commandMap.getCommand("discordid"))
        assertNotNull(harness.server.commandMap.getCommand("klrnbk-discordid"))
    }

    @Test
    fun `stopping stops the bot`() {
        registerIdentity(identityPlayer("Alice"))
        harness.load(useProxy = true)
        val facade = harness.startFully()

        facade.stop()

        assertEquals(1, harness.botMain.stops)
    }

    @Test
    fun `an unreachable database does not stop the plugin from enabling`() {
        registerIdentity(identityPlayer("Alice"))
        harness.writeConfig(useProxy = true, database = "type: MYSQL\n  host: 127.0.0.1\n  port: 1\n  database: nothing")

        val plugin = org.mockbukkit.mockbukkit.MockBukkit.load(MCPluginMain::class.java)

        assertTrue(plugin.isEnabled)
    }
}
