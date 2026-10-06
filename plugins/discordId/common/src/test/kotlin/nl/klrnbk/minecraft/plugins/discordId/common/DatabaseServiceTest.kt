package nl.klrnbk.minecraft.plugins.discordId.common

import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogEntityTable
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkCodeTable
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkTable
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class DatabaseServiceTest {
    private val env = DiscordIdTestEnvironment()

    @AfterEach
    fun cleanup() = env.close()

    private fun tableNames(): Set<String> =
        transaction(env.context.database) {
            val names = mutableSetOf<String>()
            exec("SELECT name FROM sqlite_master WHERE type = 'table'") { rs ->
                while (rs.next()) names.add(rs.getString(1))
            }
            names
        }

    // An address nothing listens on: connecting is refused straight away.
    private val unreachable = DatasourceConfig(type = DatasourceType.MYSQL, host = "127.0.0.1", port = 1, database = "nothing")

    @Test
    fun `every table of the plugin is registered for migrations and transfers`() {
        assertEquals(listOf(PlayerDiscordLinkTable, PlayerDiscordLinkCodeTable, AuditLogEntityTable), DatabaseService.TABLES)
    }

    @Test
    fun `the links and the audit log are exported, not the short-lived link codes`() {
        assertEquals(listOf(PlayerDiscordLinkTable, AuditLogEntityTable), DatabaseService.EXPORT_TABLES)
    }

    @Test
    fun `starting migrates all tables`() {
        env.start()

        assertTrue(env.datasourceProvider.isConnected())
        assertTrue(
            tableNames().containsAll(listOf("player_discord_link", "player_discord_link_code", "discord_id_audit_logs")),
            "tables: ${tableNames()}",
        )
    }

    @Test
    fun `migrating an existing database on a second boot keeps the data`() {
        env.start()
        val identityId = Uuid.random()
        env.linkRepository.create(identityId, "discord-1", "alice", false)

        // A second boot connects to the existing file with a fresh connection, which is what restart does.
        env.databaseService.restart(env.datasourceConfig, env.dataDirectory)

        assertNotNull(env.linkRepository.findByIdentityId(identityId))
    }

    @Test
    fun `cleanup deletes expired link codes and keeps valid ones`() {
        env.start()
        val expired = Uuid.random()
        val valid = Uuid.random()
        env.codeRepository.create(expired)
        env.codeRepository.create(valid)
        env.expireCode(expired)

        env.databaseService.performCleanup()

        assertNull(env.codeRepository.findByEntityId(expired))
        assertNotNull(env.codeRepository.findByEntityId(valid))
    }

    @Test
    fun `cleanup without expired codes is a no-op`() {
        env.start()

        env.databaseService.performCleanup()

        assertEquals(0, env.codeRepository.deleteExpired())
    }

    @Test
    fun `an unreachable database does not stop the plugin from starting`() {
        // start() keeps retrying in the background instead of failing the plugin startup.
        env.databaseService.start(unreachable, env.dataDirectory)

        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `restart reconnects with the given config`() {
        env.start()

        env.databaseService.restart(env.datasourceConfig, env.dataDirectory)

        assertTrue(env.datasourceProvider.isConnected())
        assertTrue(tableNames().contains("player_discord_link"))
    }

    @Test
    fun `restart to an unreachable database fails and leaves the pool closed`() {
        env.start()

        assertThrows(Exception::class.java) { env.databaseService.restart(unreachable, env.dataDirectory) }

        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `restart before start is rejected`() {
        // restart sets the config itself, so this only fails when the connection can't be made.
        assertThrows(Exception::class.java) { env.databaseService.restart(unreachable, env.dataDirectory) }
    }

    @Test
    fun `stopping disconnects`() {
        env.start()

        env.databaseService.stop()

        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `queries on a stopped database fail`() {
        env.start()
        env.databaseService.stop()

        assertThrows(Exception::class.java) { env.linkRepository.findAll() }
    }
}
