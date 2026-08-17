package nl.klrnbk.minecraft.connect.discord.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService
import nl.klrnbk.minecraft.connect.discord.services.config.VersionService
import nl.klrnbk.minecraft.connect.discord.services.database.DatabaseService
import org.slf4j.Logger

@Singleton
class AdminFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val versionService: VersionService,
        private val databaseService: DatabaseService,
    ) {
        fun reload(): Int {
            logger.info("Reloading plugin...")
            val config = configService.reload()
            val version = versionService.version

            databaseService.restart(config.database)
            logger.info("Plugin version: $version")
            logger.info("Plugin is ready.")

            return config.version
        }
    }
