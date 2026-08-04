package net.terraimperia.discord.rewards.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import net.terraimperia.discord.rewards.services.config.ConfigService
import net.terraimperia.discord.rewards.services.config.VersionService
import net.terraimperia.discord.rewards.services.database.DatabaseService
import org.slf4j.Logger

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val versionService: VersionService,
        private val databaseService: DatabaseService,
    ) {
        fun start() {
            val config = configService.load()
            val version = versionService.version

            databaseService.start(config.database)
            logger.info("Plugin version: $version")
            logger.info("Plugin is ready.")
        }
    }
