package nl.klrnbk.minecraft.packages.config.yaml

import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class YamlConfigStoreTest {
    private val configStore = YamlConfigStore()

    @Test
    fun `loadOrCreate copies default config and reads imported config classes`() {
        val dataDirectory = Files.createTempDirectory("yaml-config-load-or-create")

        val config =
            configStore.loadOrCreate<TestPluginConfig>(
                dataDirectory = dataDirectory,
                defaultResourcePath = "test-default-config.yml",
            )

        val configPath = dataDirectory.resolve("config.yml")
        assertTrue(Files.exists(configPath))
        assertEquals(true, config.useProxy)
        assertEquals("Welcome to the network", config.welcomeMessage)
        assertEquals(DatasourceType.POSTGRESQL, config.database.type)
        assertEquals("db.internal", config.database.host)
        assertEquals(5432, config.database.port)
        assertEquals(15, config.database.maximumPoolSize)
    }

    @Test
    fun `write stores kebab-case yaml keys and round-trips`() {
        val dataDirectory = Files.createTempDirectory("yaml-config-write")
        val configPath = dataDirectory.resolve("config.yml")
        val config =
            TestPluginConfig(
                useProxy = false,
                welcomeMessage = "Hello world",
                database =
                    DatasourceConfig(
                        type = DatasourceType.SQLITE,
                        database = "test.db",
                        maximumPoolSize = 5,
                    ),
            )

        configStore.write(configPath, config)

        val yaml = Files.readString(configPath)
        assertTrue(yaml.contains("use-proxy"))
        assertTrue(yaml.contains("welcome-message"))
        assertTrue(yaml.contains("maximum-pool-size"))

        val loaded = configStore.read<TestPluginConfig>(configPath)
        assertEquals(config, loaded)
    }
}

private data class TestPluginConfig(
    val useProxy: Boolean = false,
    val welcomeMessage: String = "Welcome",
    val database: DatasourceConfig = DatasourceConfig(),
)
