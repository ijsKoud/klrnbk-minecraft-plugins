import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogs
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogsServer
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerInformationCommandFacade
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerLogsCommandFacade
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerlistCommandFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.time.Instant as JavaInstant
import java.util.UUID
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IdentityPluginCommonFacadeTest {
    private fun createPlayer(name: String, online: Boolean = true) = IdentityPlayer(
        id = UUID.randomUUID(),
        playerId = UUID.randomUUID(),
        name = name,
        firstJoined = JavaInstant.ofEpochMilli(1_700_000_000_000),
        isPlayerOnline = online,
    )

    private fun unsafeInstanceOf(clazz: Class<*>): Any {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as Unsafe
        return unsafe.allocateInstance(clazz)
    }

    @Test
    fun `identity provider stores and clears api instance`() {
        val api = object : IdentityApi {
            override fun getPlayerFromUuid(uuid: UUID): IdentityPlayer? = null
            override fun getPlayerFromId(id: UUID): IdentityPlayer? = null
            override fun getPlayerFromName(name: String): IdentityPlayer? = null

            override fun getPlayersFromIds(ids: Collection<UUID>): List<IdentityPlayer> = emptyList()

            override fun getPlayerNames(prefix: String, limit: Int): List<String> = emptyList()

            override fun getAllPlayers(page: Int, itemsPerPage: Int): List<IdentityPlayer> = emptyList()

            override fun getPlayerCount(): Long = 0
        }

        IdentityProvider.unregister()
        IdentityProvider.register(api)

        assertEquals(api, IdentityProvider.get())

        IdentityProvider.unregister()
    }

    @Test
    fun `player information message builds all player details`() {
        val player = createPlayer("Alice", online = true)
        val facade = unsafeInstanceOf(PlayerInformationCommandFacade::class.java) as PlayerInformationCommandFacade
        val result = PlayerInformationCommandFacade.PlayerInformationResult(
            player = player,
            hasLogs = true,
            logsCount = "4",
        )

        val message = facade.produceMessage(result)
        val text = message.toString()

        assertTrue(text.contains("Alice"))
        assertTrue(text.contains(player.playerId.toString()))
        assertTrue(text.contains("player.info.logs"))
    }

    @Test
    fun `player logs message renders the log list and footer`() {
        val player = createPlayer("Bob", online = false)
        val facade = unsafeInstanceOf(PlayerLogsCommandFacade::class.java) as PlayerLogsCommandFacade
        val logs = listOf(
            IdentityPlayerLogs(
                id = Uuid.random(),
                playerId = player.id.toString().let { Uuid.parse(it) },
                ip = "127.0.0.2",
                action = ConnectionEventType.CONNECT_TO_PROXY,
                server = IdentityPlayerLogsServer(name = "lobby", ip = "127.0.0.1"),
                timestamp = Instant.fromEpochMilliseconds(1_700_000_050_000),
            ),
        )
        val result = PlayerLogsCommandFacade.PlayerLogsResult(
            player = player,
            logs = logs,
            nextPage = "2",
            previousPage = "1",
            totalPages = "2",
            currentPage = "1",
        )

        val message = facade.produceMessage(result, canSeePlayerIps = true)
        val text = message.toString()

        assertTrue(text.contains("Bob"))
        assertTrue(text.contains("lobby"))
        assertTrue(text.contains("127.0.0.2"))
        assertTrue(text.contains("/playerlogs"))
    }

    @Test
    fun `player list message contains page info and clickable entries`() {
        val facade = unsafeInstanceOf(PlayerlistCommandFacade::class.java) as PlayerlistCommandFacade
        val result = PlayerlistCommandFacade.PlayerListPage(
            players = listOf(
                PlayerlistCommandFacade.PlayerListPlayer("Alice", Uuid.random(), true),
                PlayerlistCommandFacade.PlayerListPlayer("Bob", Uuid.random(), false),
            ),
            currentPage = "1",
            totalPages = "3",
            nextPage = "2",
            previousPage = "1",
        )

        val message = facade.produceMessage(result)
        val text = message.toString()

        assertTrue(text.contains("Alice"))
        assertTrue(text.contains("Bob"))
        assertTrue(text.contains("/playerlist"))
        assertTrue(text.contains("3"))
    }
}
