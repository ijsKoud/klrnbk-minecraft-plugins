package nl.klrnbk.minecraft.connect.discord.services.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.config.models.DatabaseConfigModel
import org.jetbrains.exposed.v1.jdbc.Database

@Singleton
class Datasource {
    private lateinit var dataSource: HikariDataSource

    fun connect(config: DatabaseConfigModel): HikariDataSource {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = "org.postgresql.Driver"
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

    fun reconnect(config: DatabaseConfigModel) {
        disconnect()
        connect(config)
    }
}
