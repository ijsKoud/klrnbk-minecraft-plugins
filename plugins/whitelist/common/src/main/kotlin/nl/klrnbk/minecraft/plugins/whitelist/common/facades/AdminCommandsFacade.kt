package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import org.slf4j.Logger
import java.nio.file.Path

@Singleton
class AdminCommandsFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val activeStatusService: ActiveStatusService,
        private val databaseService: DatabaseService,
    ) {
        fun reload(dataDirectory: Path) {
            logger.info("Reloading plugin...")
            val config = configService.load(dataDirectory)

            activeStatusService.start(dataDirectory)
            databaseService.restart(config.database, dataDirectory)
            logger.info("Plugin is reloaded.")
        }
    }
