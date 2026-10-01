package nl.klrnbk.minecraft.packages.database

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path

class BaseDatasourceTest {
    private val directory: Path = Files.createTempDirectory("datasource-test")
    private val datasource = BaseDatasource(DatabaseContext())
    private val config = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db").also { it.dataDirectory = directory }

    @AfterEach
    fun cleanup() {
        datasource.disconnect()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `is not connected before connecting`() {
        assertFalse(datasource.isConnected())
    }

    @Test
    fun `disconnect before connect does not fail`() {
        datasource.disconnect()
    }

    @Test
    fun `is connected after connecting and not after disconnecting`() {
        datasource.connect(config)
        assertTrue(datasource.isConnected())

        datasource.disconnect()
        assertFalse(datasource.isConnected())
    }

    @Test
    fun `reconnect restores a closed connection`() {
        datasource.connect(config)
        datasource.disconnect()

        datasource.reconnect(config)

        assertTrue(datasource.isConnected())
    }

    @Test
    fun `monitor reconnects a connection that was closed`() {
        datasource.connect(config)
        val monitor =
            DatabaseConnectionMonitor(
                isHealthy = datasource::isConnected,
                reconnect = { datasource.reconnect(config) },
                logger = NOPLogger.NOP_LOGGER,
            )

        datasource.disconnect()
        assertFalse(datasource.isConnected())

        monitor.check()

        assertTrue(datasource.isConnected())
    }
}
