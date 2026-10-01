package nl.klrnbk.minecraft.plugins.whitelist.common

import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models.WhitelistPluginConfig
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models.WhitelistPluginLogsConfig
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.PlayerWhitelistRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.WhitelistLogEntityRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.WhitelistSettingsLogsEntityRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.logs.WhitelistLogsService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path

/**
 * The real services wired together on top of a temporary SQLite database.
 */
class WhitelistTestEnvironment(
    logsEnabled: Boolean = true,
) {
    val dataDirectory: Path = Files.createTempDirectory("whitelist-test")
    val datasourceConfig = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db")

    // The classes are final, so the real config service is used on top of a config file instead of a mock.
    val configService =
        ConfigService(ConfigProvider(YamlConfigStore())).also { service ->
            ConfigProvider(YamlConfigStore()).save(
                dataDirectory,
                WhitelistPluginConfig(
                    logs = WhitelistPluginLogsConfig(enabled = logsEnabled),
                    database = datasourceConfig,
                ),
            )
            service.load(dataDirectory)
        }

    val context = DatabaseContext()
    val datasourceProvider = DatasourceProvider(context)
    val logsService =
        WhitelistLogsService(
            WhitelistLogEntityRepository(context),
            WhitelistSettingsLogsEntityRepository(context),
            configService,
        )
    val databaseService = DatabaseService(datasourceProvider, logsService, NOPLogger.NOP_LOGGER)
    val playerWhitelistService = PlayerWhitelistService(context, PlayerWhitelistRepository(context), logsService)
    val activeStatusService = ActiveStatusService(logsService)

    fun start() {
        databaseService.start(datasourceConfig, dataDirectory)
        activeStatusService.start(dataDirectory)
    }

    fun close() {
        databaseService.stop()
        dataDirectory.toFile().deleteRecursively()
    }
}
