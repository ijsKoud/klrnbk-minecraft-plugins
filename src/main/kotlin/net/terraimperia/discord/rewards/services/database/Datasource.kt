package net.terraimperia.discord.rewards.services.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.inject.Singleton
import net.terraimperia.discord.rewards.services.config.models.DatabaseConfigModel
import org.jetbrains.exposed.sql.Database

@Singleton
class Datasource {
    private lateinit var dataSource: HikariDataSource

    fun connect(config: DatabaseConfigModel): HikariDataSource {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.jdbcUrl

                    username = config.username
                    password = config.password

                    maximumPoolSize = config.maximumPoolSize
                },
            )

        Database.connect(dataSource)
        return dataSource
    }

    fun disconnect() = dataSource.close()

    fun reload(config: DatabaseConfigModel) {
        disconnect()
        connect(config)
    }
}
