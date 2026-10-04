package nl.klrnbk.minecraft.plugins.identity.common

import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.models.IdentityPluginConfig
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import kotlin.uuid.Uuid

/**
 * The real services wired together on top of a temporary SQLite database.
 */
class IdentityTestEnvironment {
    val directory = Files.createTempDirectory("identity-test")
    val encryptionKey = CryptographyUtil.getRandomSecretKey()
    val context = DatabaseContext()
    val databaseConfig = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db")

    val configService =
        ConfigService(ConfigProvider(YamlConfigStore())).also {
            ConfigProvider(YamlConfigStore()).save(directory, IdentityPluginConfig(encryptionKey = encryptionKey, database = databaseConfig))
            it.load(directory)
        }

    private val playerRepository = PlayerEntityRepository(context)
    private val logRepository = PlayerConnectionLogEntityRepository(context)
    val databaseService = DatabaseService(DatasourceProvider(context), logRepository, NOPLogger.NOP_LOGGER)

    val playerDetailsService =
        PlayerDetailsService(
            playerRepository,
            object : PlayerOnlineStatusProvider {
                override fun isPlayerOnline(playerId: Uuid) = false
            },
            NOPLogger.NOP_LOGGER,
        )
    val logsService = PlayerConnectionLogsService(playerRepository, logRepository, NOPLogger.NOP_LOGGER)

    init {
        databaseService.start(databaseConfig, directory)
    }

    fun close() {
        databaseService.stop()
        directory.toFile().deleteRecursively()
    }
}
