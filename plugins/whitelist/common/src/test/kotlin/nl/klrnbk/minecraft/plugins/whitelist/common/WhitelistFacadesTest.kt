package nl.klrnbk.minecraft.plugins.whitelist.common

import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistActionResult
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistApiFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistCommandFacade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.time.Instant
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class WhitelistFacadesTest {
    private val env = WhitelistTestEnvironment().also { it.start() }

    private val alice = identityPlayer("Alice")
    private val admin = identityPlayer("Admin")

    private val commandFacade = WhitelistCommandFacade(env.playerWhitelistService, env.activeStatusService)
    private val apiFacade = WhitelistApiFacade(env.playerWhitelistService, env.activeStatusService, env.logsService)

    init {
        registerIdentity(alice, admin)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    // Command facade

    @Test
    fun `adding a player by name whitelists them, attributed to the sender`() {
        val result = commandFacade.addPlayer("alice", admin.playerId)

        assertEquals(WhitelistActionResult.SUCCESS, result)
        assertTrue(apiFacade.isPlayerWhitelisted(alice.id))
        assertEquals(admin.id, apiFacade.getPlayerLogs(alice.id).single().actorId)
    }

    @Test
    fun `the console is logged with the console actor id`() {
        commandFacade.addPlayer("Alice", null)

        assertEquals(WhitelistApi.CONSOLE_ACTOR_ID, apiFacade.getPlayerLogs(alice.id).single().actorId)
    }

    @Test
    fun `adding twice reports no change`() {
        commandFacade.addPlayer("Alice", null)

        assertEquals(WhitelistActionResult.NO_CHANGE, commandFacade.addPlayer("Alice", null))
    }

    @Test
    fun `removing a player by name`() {
        commandFacade.addPlayer("Alice", null)

        assertEquals(WhitelistActionResult.SUCCESS, commandFacade.removePlayer("Alice", admin.playerId))
        assertFalse(apiFacade.isPlayerWhitelisted(alice.id))
        assertEquals(WhitelistActionResult.NO_CHANGE, commandFacade.removePlayer("Alice", admin.playerId))
    }

    @Test
    fun `unknown players are reported`() {
        assertEquals(WhitelistActionResult.PLAYER_NOT_FOUND, commandFacade.addPlayer("Nobody", null))
        assertEquals(WhitelistActionResult.PLAYER_NOT_FOUND, commandFacade.removePlayer("Nobody", null))
    }

    @Test
    fun `senders unknown to identity can't perform actions`() {
        val stranger = UUID.randomUUID()

        assertEquals(WhitelistActionResult.ACTOR_NOT_FOUND, commandFacade.addPlayer("Alice", stranger))
        assertEquals(WhitelistActionResult.ACTOR_NOT_FOUND, commandFacade.removePlayer("Alice", stranger))
        assertEquals(WhitelistActionResult.ACTOR_NOT_FOUND, commandFacade.setWhitelistEnabled(true, stranger))
        assertFalse(apiFacade.isPlayerWhitelisted(alice.id))
        assertFalse(apiFacade.isWhitelistEnabled())
    }

    @Test
    fun `toggling the whitelist through the command facade`() {
        assertEquals(WhitelistActionResult.SUCCESS, commandFacade.setWhitelistEnabled(true, admin.playerId))
        assertTrue(apiFacade.isWhitelistEnabled())
        assertEquals(WhitelistActionResult.NO_CHANGE, commandFacade.setWhitelistEnabled(true, admin.playerId))

        assertEquals(WhitelistActionResult.SUCCESS, commandFacade.setWhitelistEnabled(false, null))
        assertFalse(apiFacade.isWhitelistEnabled())
    }

    // API facade

    @Test
    fun `api exposes adding and removing with change results`() {
        assertTrue(apiFacade.addPlayerToWhitelist(alice.id, admin.id))
        assertFalse(apiFacade.addPlayerToWhitelist(alice.id, admin.id))
        assertTrue(apiFacade.isPlayerWhitelisted(alice.id))

        assertTrue(apiFacade.removePlayerFromWhitelist(alice.id, admin.id))
        assertFalse(apiFacade.removePlayerFromWhitelist(alice.id, admin.id))
        assertFalse(apiFacade.isPlayerWhitelisted(alice.id))
    }

    @Test
    fun `api exposes player logs with a count`() {
        apiFacade.addPlayerToWhitelist(alice.id, admin.id)
        Thread.sleep(5)
        apiFacade.removePlayerFromWhitelist(alice.id, admin.id)

        val logs = apiFacade.getPlayerLogs(alice.id)

        assertEquals(2, apiFacade.getPlayerLogsCount(alice.id))
        assertEquals(listOf(false, true), logs.map { it.isWhitelisted })
        assertEquals(alice.id, logs.first().playerId)
        assertEquals(admin.id, logs.first().actorId)
    }

    @Test
    fun `api exposes settings logs with a count`() {
        apiFacade.setWhitelistEnabled(true, admin.id)
        Thread.sleep(5)
        apiFacade.setWhitelistEnabled(false, admin.id)

        assertEquals(2, apiFacade.getSettingsLogsCount())
        assertEquals(listOf(false, true), apiFacade.getSettingsLogs().map { it.isWhitelistEnabled })
        assertEquals(1, apiFacade.getSettingsLogs(page = 1, itemsPerPage = 1).size)
    }

    @Test
    fun `api rejects invalid pagination`() {
        assertThrows(IllegalArgumentException::class.java) { apiFacade.getPlayerLogs(alice.id, page = -1) }
        assertThrows(IllegalArgumentException::class.java) { apiFacade.getSettingsLogs(itemsPerPage = 0) }
    }

    // Join check

    private fun joinFacade(enabled: Boolean): PlayerWhitelistFacade {
        env.activeStatusService.setWhitelistEnabled(enabled, admin.id.toKotlinUuid())
        return PlayerWhitelistFacade(env.playerWhitelistService, env.activeStatusService, env.configService)
    }

    @Test
    fun `everyone may join while the whitelist is disabled, without asking identity`() {
        IdentityProvider.unregister()

        assertTrue(joinFacade(enabled = false).isPlayerAllowedToJoinServer(UUID.randomUUID().toKotlinUuid(), "Anyone"))
    }

    @Test
    fun `only whitelisted players may join while the whitelist is enabled`() {
        val facade = joinFacade(enabled = true)

        assertFalse(facade.isPlayerAllowedToJoinServer(alice.playerId.toKotlinUuid(), "Alice"))

        apiFacade.addPlayerToWhitelist(alice.id, admin.id)
        assertTrue(facade.isPlayerAllowedToJoinServer(alice.playerId.toKotlinUuid(), "Alice"))
        assertFalse(facade.isPlayerAllowedToJoinServer(admin.playerId.toKotlinUuid(), "Admin"))
    }

    @Test
    fun `players unknown to identity are denied while the whitelist is enabled`() {
        val result = joinFacade(enabled = true).checkJoin(UUID.randomUUID().toKotlinUuid(), "Stranger")

        assertEquals(PlayerWhitelistFacade.JoinCheckResult.UNKNOWN_PLAYER, result)
        assertFalse(result.isAllowed)
    }

    @Test
    fun `clients without a uuid are checked by name`() {
        val facade = joinFacade(enabled = true)
        assertEquals(PlayerWhitelistFacade.JoinCheckResult.NOT_WHITELISTED, facade.checkJoin(null, "alice"))

        apiFacade.addPlayerToWhitelist(alice.id, admin.id)

        assertEquals(PlayerWhitelistFacade.JoinCheckResult.WHITELISTED, facade.checkJoin(null, "alice"))
        assertEquals(PlayerWhitelistFacade.JoinCheckResult.UNKNOWN_PLAYER, facade.checkJoin(null, "Nobody"))
    }

    // Whitelisted players

    @Test
    fun `api lists whitelisted players, newest first, without removed ones`() {
        val bob = identityPlayer("Bob")
        (IdentityProvider.get() as FakeIdentityApi).players.add(bob)
        apiFacade.addPlayerToWhitelist(alice.id, admin.id)
        Thread.sleep(5)
        apiFacade.addPlayerToWhitelist(bob.id, WhitelistApi.CONSOLE_ACTOR_ID)
        Thread.sleep(5)
        apiFacade.addPlayerToWhitelist(admin.id, admin.id)
        apiFacade.removePlayerFromWhitelist(admin.id, admin.id)

        val players = apiFacade.getWhitelistedPlayers()

        assertEquals(2, apiFacade.getWhitelistedPlayersCount())
        assertEquals(listOf(bob.id, alice.id), players.map { it.playerId })
        assertEquals(listOf(WhitelistApi.CONSOLE_ACTOR_ID, admin.id), players.map { it.actorId })
        assertEquals(listOf(alice.id), apiFacade.getWhitelistedPlayers(page = 1, itemsPerPage = 1).map { it.playerId })
    }

    // Suggestions

    @Test
    fun `player suggestions come from identity, not from who is online`() {
        assertEquals(listOf("Admin", "Alice"), commandFacade.suggestPlayerNames("a"))
        assertEquals(listOf("Alice"), commandFacade.suggestPlayerNames("ALI"))
        assertEquals(listOf("Admin", "Alice"), commandFacade.suggestPlayerNames(""))
    }

    @Test
    fun `remove suggestions only contain whitelisted players`() {
        apiFacade.addPlayerToWhitelist(alice.id, admin.id)

        assertEquals(listOf("Alice"), commandFacade.suggestWhitelistedPlayerNames(""))
        assertEquals(emptyList<String>(), commandFacade.suggestWhitelistedPlayerNames("ad"))
    }

    // Reload

    @Test
    fun `reload rereads the config and status file and reconnects`() {
        apiFacade.setWhitelistEnabled(true, admin.id)
        env.dataDirectory.resolve(WHITELIST_ACTIVE_STATUS_FILE_NAME).toFile().writeText("false")
        assertTrue(apiFacade.isWhitelistEnabled(), "status is cached until a reload")
        env.datasourceProvider.disconnect()

        AdminCommandsFacade(NOPLogger.NOP_LOGGER, env.configService, env.activeStatusService, env.databaseService)
            .reload(env.dataDirectory)

        assertFalse(apiFacade.isWhitelistEnabled())
        assertTrue(env.datasourceProvider.isConnected())
    }
}
