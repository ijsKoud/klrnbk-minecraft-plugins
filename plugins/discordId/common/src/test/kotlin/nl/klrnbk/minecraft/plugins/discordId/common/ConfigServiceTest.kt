package nl.klrnbk.minecraft.plugins.discordId.common

import net.dv8tion.jda.api.entities.Activity
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models.DiscordIdPluginConfig
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

class ConfigServiceTest {
    private val directory = Files.createTempDirectory("discord-id-config")
    private val service = ConfigService(ConfigProvider(YamlConfigStore()))

    @AfterEach
    fun cleanup() {
        directory.toFile().deleteRecursively()
    }

    private fun writeConfig(content: String) = Files.writeString(directory.resolve("config.yml"), content)

    @Test
    fun `the config is not available before it has been loaded`() {
        assertThrows(IllegalStateException::class.java) { service.getConfig() }
    }

    @Test
    fun `a config file in the data directory is read`() {
        writeConfig(
            """
            use-proxy: true
            discord:
              booster-role: '1234'
              unlink-cooldown: 5000
              status-message: 'Hello'
              status-type: 2
              bot-token: 'secret'
            database:
              type: POSTGRESQL
              host: db.example.com
              port: 5432
              database: discord
              username: me
              password: hunter2
            """.trimIndent(),
        )

        val config = service.load(directory)

        assertTrue(config.useProxy)
        assertEquals("1234", config.discord.boosterRole)
        assertEquals(5000L, config.discord.unlinkCooldown)
        assertEquals("Hello", config.discord.statusMessage)
        assertEquals(Activity.ActivityType.LISTENING, config.discord.statusType)
        assertEquals("secret", config.discord.botToken)
        assertEquals(DatasourceType.POSTGRESQL, config.database.type)
        assertEquals("db.example.com", config.database.host)
        assertEquals(5432, config.database.port)
    }

    @Test
    fun `options that are missing fall back to their defaults`() {
        writeConfig("use-proxy: false\n")

        val config = service.load(directory)

        assertNull(config.discord.boosterRole)
        assertNull(config.discord.botToken)
        assertEquals(30.days.toLong(DurationUnit.MILLISECONDS), config.discord.unlinkCooldown)
        assertEquals(Activity.ActivityType.WATCHING, config.discord.statusType)
        assertEquals(DatasourceType.SQLITE, config.database.type)
    }

    @Test
    fun `getConfig returns what was loaded last, so a reload picks up edits`() {
        writeConfig("discord:\n  status-message: 'first'\n")
        service.load(directory)
        writeConfig("discord:\n  status-message: 'second'\n")

        assertEquals("first", service.getConfig().discord.statusMessage)
        service.load(directory)
        assertEquals("second", service.getConfig().discord.statusMessage)
    }

    @Test
    fun `loading without a config file or bundled default fails`() {
        // This module bundles no config.yml (the platform modules do), so there is nothing to copy.
        assertThrows(FileNotFoundException::class.java) { service.load(directory) }
    }

    @Test
    fun `a saved config can be loaded again`() {
        val provider = ConfigProvider(YamlConfigStore())
        provider.save(directory, DiscordIdPluginConfig(useProxy = true))

        assertTrue(service.load(directory).useProxy)
        assertFalse(DiscordIdPluginConfig().useProxy)
    }

    @Test
    fun `a config that is not valid yaml is rejected`() {
        writeConfig("discord: [unterminated")

        assertThrows(Exception::class.java) { service.load(directory) }
    }
}
