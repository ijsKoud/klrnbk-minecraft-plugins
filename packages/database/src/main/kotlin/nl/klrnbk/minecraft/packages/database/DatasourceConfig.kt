package nl.klrnbk.minecraft.packages.database

import java.nio.file.Path

data class DatasourceConfig(
    val type: DatasourceType = DatasourceType.SQLITE,
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "database.db",
    val username: String = "root",
    val password: String = "password",
    val maximumPoolSize: Int = 10,
) {
    var dataDirectory: Path? = null

    fun getJdbcUrl(): String =
        when (type) {
            DatasourceType.POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
            DatasourceType.SQLITE -> "jdbc:sqlite:${dataDirectory?.resolve(database) ?: database}"
            DatasourceType.MYSQL -> "jdbc:mariadb://$host:$port/$database"
        }

    fun getDriverClassName(): String =
        when (type) {
            DatasourceType.POSTGRESQL -> "org.postgresql.Driver"
            DatasourceType.SQLITE -> "org.sqlite.JDBC"
            DatasourceType.MYSQL -> "org.mariadb.jdbc.Driver"
        }
}
