package nl.klrnbk.minecraft.plugins.identity.common

import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

class DatabaseServiceTest {
    private val directory = Files.createTempDirectory("identity-db-test")
    private val config = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db")
    private val provider = DatasourceProvider(DatabaseContext())
    private val service =
        DatabaseService(provider, PlayerConnectionLogEntityRepository(DatabaseContext()), NOPLogger.NOP_LOGGER)

    @AfterEach
    fun cleanup() {
        service.stop()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `start connects`() {
        service.start(config, directory)

        assertTrue(provider.isConnected())
    }

    @Test
    fun `a lost connection is restored in the background`() {
        service.start(config, directory)
        provider.disconnect()
        assertFalse(provider.isConnected())

        val deadline = System.nanoTime() + 20.seconds.inWholeNanoseconds
        while (!provider.isConnected() && System.nanoTime() < deadline) Thread.sleep(100)

        assertTrue(provider.isConnected())
    }

    @Test
    fun `an unreachable database at startup does not throw and is retried`() {
        service.start(DatasourceConfig(type = DatasourceType.POSTGRESQL, host = "127.0.0.1", port = 1, database = "identity"), directory)

        assertFalse(provider.isConnected())
    }

    @Test
    fun `stop disconnects and stays disconnected`() {
        service.start(config, directory)

        service.stop()

        assertFalse(provider.isConnected())
    }
}
