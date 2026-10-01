package nl.klrnbk.minecraft.plugins.whitelist.common

import nl.klrnbk.minecraft.packages.database.QueryPagination
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.uuid.Uuid

class WhitelistServicesTest {
    private val player = Uuid.random()
    private val actor = Uuid.random()
    private var env = WhitelistTestEnvironment().also { it.start() }

    @AfterEach
    fun cleanup() = env.close()

    private fun useEnvironment(logsEnabled: Boolean) {
        env.close()
        env = WhitelistTestEnvironment(logsEnabled).also { it.start() }
    }

    @Test
    fun `players are not whitelisted by default`() {
        assertFalse(env.playerWhitelistService.isPlayerWhitelisted(player))
    }

    @Test
    fun `adding a player whitelists them and logs who did it`() {
        assertTrue(env.playerWhitelistService.addPlayerToWhitelist(player, actor))

        assertTrue(env.playerWhitelistService.isPlayerWhitelisted(player))

        val logs = env.logsService.getPlayerLogs(player, QueryPagination())
        assertEquals(1, logs.size)
        assertEquals(player.toString(), logs.single().playerId.toString())
        assertEquals(actor.toString(), logs.single().actorId.toString())
        assertTrue(logs.single().isWhitelisted)
    }

    @Test
    fun `adding an already whitelisted player changes and logs nothing`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)

        assertFalse(env.playerWhitelistService.addPlayerToWhitelist(player, actor))

        assertEquals(1, env.logsService.getPlayerLogsCount(player))
    }

    @Test
    fun `removing a player un-whitelists them and logs who did it`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)
        val remover = Uuid.random()

        assertTrue(env.playerWhitelistService.removePlayerFromWhitelist(player, remover))

        assertFalse(env.playerWhitelistService.isPlayerWhitelisted(player))
        val newest = env.logsService.getPlayerLogs(player, QueryPagination()).first()
        assertFalse(newest.isWhitelisted)
        assertEquals(remover.toString(), newest.actorId.toString())
    }

    @Test
    fun `removing a player that is not whitelisted changes and logs nothing`() {
        assertFalse(env.playerWhitelistService.removePlayerFromWhitelist(player, actor))

        assertEquals(0, env.logsService.getPlayerLogsCount(player))
    }

    @Test
    fun `a player can be whitelisted again after being removed`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)
        env.playerWhitelistService.removePlayerFromWhitelist(player, actor)

        assertTrue(env.playerWhitelistService.addPlayerToWhitelist(player, actor))

        assertTrue(env.playerWhitelistService.isPlayerWhitelisted(player))
        assertEquals(3, env.logsService.getPlayerLogsCount(player))
    }

    @Test
    fun `player logs are newest first and paginated`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)
        Thread.sleep(5)
        env.playerWhitelistService.removePlayerFromWhitelist(player, actor)
        Thread.sleep(5)
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)

        val firstPage = env.logsService.getPlayerLogs(player, QueryPagination(page = 0, itemsPerPage = 2))
        val secondPage = env.logsService.getPlayerLogs(player, QueryPagination(page = 1, itemsPerPage = 2))

        assertEquals(listOf(true, false), firstPage.map { it.isWhitelisted })
        assertEquals(listOf(true), secondPage.map { it.isWhitelisted })
    }

    @Test
    fun `player logs only contain the requested player`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)
        env.playerWhitelistService.addPlayerToWhitelist(Uuid.random(), actor)

        assertEquals(1, env.logsService.getPlayerLogs(player, QueryPagination()).size)
    }

    @Test
    fun `nothing is logged when logging is disabled but the action still happens`() {
        useEnvironment(logsEnabled = false)

        assertTrue(env.playerWhitelistService.addPlayerToWhitelist(player, actor))
        assertTrue(env.activeStatusService.setWhitelistEnabled(true, actor))

        assertTrue(env.playerWhitelistService.isPlayerWhitelisted(player))
        assertTrue(env.activeStatusService.isWhitelistEnabled())
        assertEquals(0, env.logsService.getPlayerLogsCount(player))
        assertEquals(0, env.logsService.getSettingsLogsCount())
    }

    @Test
    fun `whitelist is disabled by default`() {
        assertFalse(env.activeStatusService.isWhitelistEnabled())
    }

    @Test
    fun `toggling the whitelist changes the status and logs who did it`() {
        assertTrue(env.activeStatusService.setWhitelistEnabled(true, actor))
        assertTrue(env.activeStatusService.isWhitelistEnabled())

        Thread.sleep(5)
        val other = Uuid.random()
        assertTrue(env.activeStatusService.setWhitelistEnabled(false, other))
        assertFalse(env.activeStatusService.isWhitelistEnabled())

        val logs = env.logsService.getSettingsLogs(QueryPagination())
        assertEquals(listOf(false, true), logs.map { it.isWhitelistEnabled })
        assertEquals(listOf(other.toString(), actor.toString()), logs.map { it.actorId.toString() })
    }

    @Test
    fun `toggling to the current status changes and logs nothing`() {
        assertFalse(env.activeStatusService.setWhitelistEnabled(false, actor))

        env.activeStatusService.setWhitelistEnabled(true, actor)
        assertFalse(env.activeStatusService.setWhitelistEnabled(true, actor))

        assertEquals(1, env.logsService.getSettingsLogsCount())
    }

    @Test
    fun `whitelist status survives a restart and a reload`() {
        env.activeStatusService.setWhitelistEnabled(true, actor)

        val restarted = WhitelistTestEnvironment().also { it.activeStatusService.start(env.dataDirectory) }

        assertTrue(restarted.activeStatusService.isWhitelistEnabled())
        restarted.dataDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `log cleanup removes old logs of both kinds`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)
        env.activeStatusService.setWhitelistEnabled(true, actor)
        Thread.sleep(10)

        val deleted = env.logsService.deleteLogsOlderThan(Duration.ZERO)

        assertEquals(2, deleted)
        assertEquals(0, env.logsService.getPlayerLogsCount(player))
        assertEquals(0, env.logsService.getSettingsLogsCount())
    }

    @Test
    fun `log cleanup keeps logs within the retention`() {
        env.playerWhitelistService.addPlayerToWhitelist(player, actor)

        val deleted = env.logsService.deleteLogsOlderThan(Duration.parse("1d"))

        assertEquals(0, deleted)
        assertEquals(1, env.logsService.getPlayerLogsCount(player))
    }
}
