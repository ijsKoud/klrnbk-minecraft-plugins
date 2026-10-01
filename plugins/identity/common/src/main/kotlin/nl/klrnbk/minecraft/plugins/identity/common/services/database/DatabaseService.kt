package nl.klrnbk.minecraft.plugins.identity.common.services.database

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.DatabaseConnectionMonitor
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntityTable
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntityTable
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration

@Singleton
class DatabaseService
    @Inject
    constructor(
        private val datasourceProvider: DatasourceProvider,
        private val playerConnectionLogEntityRepository: PlayerConnectionLogEntityRepository,
        private val logger: Logger,
    ) {
        private var config: DatasourceConfig? = null

        private val connectionMonitor =
            DatabaseConnectionMonitor(
                isHealthy = datasourceProvider::isConnected,
                reconnect = ::reconnect,
                logger = logger,
            )

        /**
         * Reconnects with a (possibly changed) config, used when the plugin is reloaded.
         */
        @Synchronized
        fun restart(
            config: DatasourceConfig,
            dataDirectory: Path,
        ) {
            logger.info("Restarting database connection...")

            this.config = config.also { it.dataDirectory = dataDirectory }
            connectAndMigrate(reconnect = true)

            logger.info("Database connection restarted.")
        }

        fun performLogsCleanup(retention: Duration) {
            val cutoff = Clock.System.now().minus(retention)
            logger.info("Deleting player connection logs before $cutoff...")

            val amount = playerConnectionLogEntityRepository.deleteBefore(cutoff)
            logger.info("Deleted $amount player connection logs before $cutoff")
        }

        /**
         * Connects to the database and keeps the connection alive: if it drops (or the
         * database is unreachable right now) it is re-established in the background,
         * with increasing delays between failed attempts.
         */
        fun start(
            config: DatasourceConfig,
            dataDirectory: Path,
        ) {
            logger.info("Connecting to database...")

            synchronized(this) {
                this.config = config.also { it.dataDirectory = dataDirectory }

                try {
                    connectAndMigrate(reconnect = false)
                    logger.info("Database connected.")
                } catch (exception: Exception) {
                    logger.error("Could not connect to the database, will keep retrying in the background.", exception)
                }
            }

            connectionMonitor.start()
        }

        fun stop() {
            // Stop the monitor first, otherwise it would reconnect right after we disconnect.
            connectionMonitor.stop()

            synchronized(this) {
                logger.info("Disconnecting from database...")
                datasourceProvider.disconnect()
                logger.info("Database disconnected.")
            }
        }

        fun migrations() {
            logger.info("Performing database migrations...")

            transaction(datasourceProvider.database) {
                val statements =
                    MigrationUtils.statementsRequiredForDatabaseMigration(
                        PlayerEntityTable,
                        PlayerConnectionLogEntityTable,
                        withLogs = true,
                    )

                statements.forEach { sql ->
                    TransactionManager.current().exec(sql)
                }
            }

            logger.info("Database migrations completed.")
        }

        @Synchronized
        private fun reconnect() {
            connectAndMigrate(reconnect = true)
        }

        private fun connectAndMigrate(reconnect: Boolean) {
            val config = checkNotNull(config) { "The database service has not been started yet." }

            try {
                if (reconnect) datasourceProvider.reconnect(config) else datasourceProvider.connect(config)
                migrations()
            } catch (exception: Exception) {
                // Don't leave a pool open without a finished migration, the monitor retries whenever the pool is unhealthy.
                datasourceProvider.disconnect()
                throw exception
            }
        }
    }
