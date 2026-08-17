package nl.klrnbk.minecraft.connect.discord.services.database

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.config.models.DatabaseConfigModel
import nl.klrnbk.minecraft.connect.discord.services.database.tables.DiscordLinkRequestCodeTable
import nl.klrnbk.minecraft.connect.discord.services.database.tables.DiscordLinkTable
import nl.klrnbk.minecraft.connect.discord.services.database.tables.PlayerRegistryDetailsTable
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.Logger

@Singleton
class DatabaseService
    @Inject
    constructor(
        private val datasource: Datasource,
        private val logger: Logger,
    ) {
        fun restart(config: DatabaseConfigModel) {
            logger.info("Restarting database connection...")

            datasource.reconnect(config)
            migrations()

            logger.info("Database connection restarted.")
        }

        fun start(config: DatabaseConfigModel) {
            logger.info("Connecting to database...")

            datasource.connect(config)
            migrations()

            logger.info("Database connected.")
        }

        fun migrations() {
            logger.debug("Performing database migrations...")

            query {
                val statements =
                    MigrationUtils.statementsRequiredForDatabaseMigration(
                        *arrayOf(DiscordLinkTable, DiscordLinkRequestCodeTable, PlayerRegistryDetailsTable),
                        withLogs = false,
                    )

                statements.forEach { sql ->
                    TransactionManager.current().exec(sql)
                }
            }

            logger.debug("Database migrations completed.")
        }

        fun <T> query(block: Transaction.() -> T): T =
            transaction {
                block()
            }
    }
