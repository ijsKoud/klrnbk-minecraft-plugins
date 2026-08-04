package net.terraimperia.discord.rewards.services.config.models

import net.terraimperia.discord.rewards.services.config.enums.DatabaseConfigType
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.objectmapping.meta.Setting

@ConfigSerializable
class DatabaseConfigModel {
    @Comment("The type of the database, currently supported options: POSTGRESQL, MYSQL (MariaDB)")
    val type: DatabaseConfigType = DatabaseConfigType.POSTGRESQL

    @Setting("jdbc-url")
    @Comment("The JDBC URL for the database connection. Format: jdbc:postgresql://host:port/dbname")
    val jdbcUrl: String = "jdbc:postgresql://host:5432/dbname"

    @Comment("The username for the database connection")
    val username: String = "postgres"

    @Comment("The password for the database connection")
    val password: String = "postgres"

    @Setting("max-pool-size")
    @Comment("The maximum size of the database connection pool")
    val maximumPoolSize: Int = 10
}
