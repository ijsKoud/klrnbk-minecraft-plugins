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
    @Comment("The JDBC URL for the database connection. Format: jdbc:postgresql://user:password@host:port/dbname")
    val jdbcUrl: String = "jdbc:postgresql://user:password@host:5432/dbname"
}
