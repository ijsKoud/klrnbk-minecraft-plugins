package nl.klrnbk.minecraft.plugins.whitelist.common

import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class DatabaseServiceTest {
    private val env = WhitelistTestEnvironment()

    @AfterEach
    fun cleanup() = env.close()

    @Test
    fun `start connects and creates all tables`() {
        env.start()

        assertTrue(env.datasourceProvider.isConnected())
        // Would throw when a table is missing.
        assertEquals(0, env.logsService.getSettingsLogsCount())
        assertEquals(0, env.logsService.getPlayerLogsCount(Uuid.random()))
        assertFalse(env.playerWhitelistService.isPlayerWhitelisted(Uuid.random()))
    }

    @Test
    fun `restart keeps the stored data`() {
        env.start()
        val player = Uuid.random()
        env.playerWhitelistService.addPlayerToWhitelist(player, Uuid.random())

        env.databaseService.restart(env.datasourceConfig, env.dataDirectory)

        assertTrue(env.datasourceProvider.isConnected())
        assertTrue(env.playerWhitelistService.isPlayerWhitelisted(player))
    }

    @Test
    fun `stop disconnects`() {
        env.start()

        env.databaseService.stop()

        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `an unreachable database at startup does not crash the plugin`() {
        val unreachable =
            DatasourceConfig(
                type = DatasourceType.POSTGRESQL,
                host = "127.0.0.1",
                port = 1,
                database = "whitelist",
            )

        env.databaseService.start(unreachable, env.dataDirectory)

        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `a lost connection is restored in the background`() {
        env.start()
        val player = Uuid.random()
        env.playerWhitelistService.addPlayerToWhitelist(player, Uuid.random())

        env.datasourceProvider.disconnect()
        assertFalse(env.datasourceProvider.isConnected())

        // The monitor checks every few seconds.
        val deadline = System.nanoTime() + 20.seconds.inWholeNanoseconds
        while (!env.datasourceProvider.isConnected() && System.nanoTime() < deadline) Thread.sleep(100)

        assertTrue(env.datasourceProvider.isConnected())
        assertTrue(env.playerWhitelistService.isPlayerWhitelisted(player))
    }
}
