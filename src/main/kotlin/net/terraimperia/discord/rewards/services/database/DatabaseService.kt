package net.terraimperia.discord.rewards.services.database

import com.google.inject.Inject
import com.google.inject.Singleton
import com.zaxxer.hikari.HikariDataSource
import net.terraimperia.discord.rewards.services.config.models.DatabaseConfigModel
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger

@Singleton
class DatabaseService
    @Inject
    constructor(
        private val datasource: Datasource,
        private val logger: Logger,
    ) {
        fun start(config: DatabaseConfigModel) {
            logger.info("Connecting to database...")

            val source = datasource.connect(config)
            migrations(source)

            logger.info("Database connected.")
        }

        fun migrations(dataSource: HikariDataSource) {
            logger.debug("Performing database migrations...")

            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()

            logger.debug("Database migrations completed.")
        }

        fun <T> query(block: Transaction.() -> T): T =
            transaction {
                block()
            }
    }
