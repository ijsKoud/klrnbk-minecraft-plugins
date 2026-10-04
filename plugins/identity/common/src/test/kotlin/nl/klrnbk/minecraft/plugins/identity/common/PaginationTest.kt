package nl.klrnbk.minecraft.plugins.identity.common

import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerLogsCommandFacade
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerlistCommandFacade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class PaginationTest {
    private val env = IdentityTestEnvironment()
    private val playerList = PlayerlistCommandFacade(env.playerDetailsService)
    private val playerLogs = PlayerLogsCommandFacade(env.playerDetailsService, env.logsService, env.configService)

    @AfterEach
    fun cleanup() = env.close()

    private fun addPlayers(count: Int) = repeat(count) { env.playerDetailsService.upsert(Uuid.random(), "Player%02d".format(it)) }

    private fun addLogs(
        playerId: Uuid,
        count: Int,
    ) = repeat(count) {
        env.logsService.addLogEntry(playerId, ConnectionEventType.CONNECT_TO_PROXY, "server$it", "name$it", null, env.encryptionKey)
        Thread.sleep(2)
    }

    // Player list

    @Test
    fun `the player list reports the exact number of pages`() {
        val expected = mapOf(0 to 1, 1 to 1, 9 to 1, 10 to 1, 11 to 2, 20 to 2, 21 to 3)

        var added = 0
        expected.forEach { (players, pages) ->
            addPlayers(players - added)
            added = players

            assertEquals(pages.toString(), playerList.getPlayerList(1).totalPages, "$players players")
        }
    }

    @Test
    fun `the player list pages contain the right players`() {
        addPlayers(25)

        val first = playerList.getPlayerList(1)
        val last = playerList.getPlayerList(3)

        assertEquals((0..9).map { "Player%02d".format(it) }, first.players.map { it.name })
        assertEquals((20..24).map { "Player%02d".format(it) }, last.players.map { it.name })
        assertEquals("3", last.totalPages)
        assertEquals("3", last.nextPage)
        assertEquals("1", first.previousPage)
        assertEquals("2", first.nextPage)
    }

    // Player logs

    @Test
    fun `player logs start with the newest logs on page one`() {
        val player = env.playerDetailsService.upsert(Uuid.random(), "Alice")
        val playerId = Uuid.parse(player.playerId.toString())
        addLogs(playerId, 25)

        val first = playerLogs.getPlayerLogs("Alice", 1)!!
        val last = playerLogs.getPlayerLogs("Alice", 3)!!

        assertEquals("3", first.totalPages)
        assertEquals((24 downTo 15).map { "name$it" }, first.logs.map { it.server.name })
        assertEquals((4 downTo 0).map { "name$it" }, last.logs.map { it.server.name })
    }

    @Test
    fun `player logs report the exact number of pages`() {
        val player = env.playerDetailsService.upsert(Uuid.random(), "Alice")
        val playerId = Uuid.parse(player.playerId.toString())

        assertEquals("1", playerLogs.getPlayerLogs("Alice", 1)!!.totalPages)
        addLogs(playerId, 10)
        assertEquals("1", playerLogs.getPlayerLogs("Alice", 1)!!.totalPages)
        addLogs(playerId, 1)
        assertEquals("2", playerLogs.getPlayerLogs("Alice", 1)!!.totalPages)
        assertEquals(1, playerLogs.getPlayerLogs("Alice", 2)!!.logs.size)
    }

    // Deep pages

    @Test
    fun `deep player list pages are right when jumped to and when walked to`() {
        addPlayers(95)
        val names = (0 until 95).map { "Player%02d".format(it) }

        // Cold: nothing is remembered about where page 10 starts.
        assertEquals(names.drop(90), playerList.getPlayerList(10).players.map { it.name })
        assertEquals("10", playerList.getPlayerList(10).totalPages)

        val walked = (1..10).flatMap { playerList.getPlayerList(it).players.map { player -> player.name } }

        assertEquals(names, walked)
        assertEquals(names.subList(40, 50), playerList.getPlayerList(5).players.map { it.name })
    }

    @Test
    fun `player logs can be walked page by page without gaps or repeats`() {
        val player = env.playerDetailsService.upsert(Uuid.random(), "Alice")
        addLogs(Uuid.parse(player.playerId.toString()), 55)

        val pages = (1..6).map { playerLogs.getPlayerLogs("Alice", it)!!.logs.map { log -> log.server.name } }

        assertEquals(listOf(10, 10, 10, 10, 10, 5), pages.map { it.size })
        assertEquals((54 downTo 0).map { "name$it" }, pages.flatten())
        // Going back works as well.
        assertEquals(pages[2], playerLogs.getPlayerLogs("Alice", 3)!!.logs.map { it.server.name })
    }
}
