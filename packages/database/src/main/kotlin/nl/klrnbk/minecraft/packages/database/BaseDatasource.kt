package nl.klrnbk.minecraft.packages.database

import com.google.inject.Singleton
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database

@Singleton
class DatabaseContext {
    lateinit var database: Database
}

open class BaseDatasource(
    val context: DatabaseContext,
) {
    val database: Database
        get() = context.database
    private lateinit var dataSource: HikariDataSource

    fun connect(config: DatasourceConfig): HikariDataSource {
        // Only replace the current datasource once the new pool is up, so a failed connect
        // (e.g. database offline) leaves the previous, closed pool in place instead of a half-built one.
        val newDataSource =
            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = config.getDriverClassName()
                    jdbcUrl = config.getJdbcUrl()

                    username = config.username
                    password = config.password

                    maximumPoolSize = config.maximumPoolSize
                    connectionTimeout = CONNECTION_TIMEOUT_MILLIS
                },
            )

        dataSource = newDataSource
        context.database = Database.connect(newDataSource)
        return newDataSource
    }

    fun disconnect() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    fun reconnect(config: DatasourceConfig) {
        disconnect()
        connect(config)
    }

    /**
     * Whether the datasource is open and is able to hand out a working connection.
     */
    fun isConnected(): Boolean {
        if (!::dataSource.isInitialized || dataSource.isClosed) return false

        return try {
            dataSource.connection.use { it.isValid(VALIDATION_TIMEOUT_SECONDS) }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 5_000L
        const val VALIDATION_TIMEOUT_SECONDS = 2
    }
}
