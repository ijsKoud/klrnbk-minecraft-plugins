package nl.klrnbk.minecraft.plugins.identity.common

import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class PlayerDetailsServiceTest {
    private val directory = Files.createTempDirectory("identity-details-test")
    private val context = DatabaseContext()
    private val databaseService =
        DatabaseService(DatasourceProvider(context), PlayerConnectionLogEntityRepository(context), NOPLogger.NOP_LOGGER)
    private val service =
        PlayerDetailsService(
            PlayerEntityRepository(context),
            object : PlayerOnlineStatusProvider {
                override fun isPlayerOnline(playerId: Uuid) = false
            },
            NOPLogger.NOP_LOGGER,
        )

    init {
        databaseService.start(DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db"), directory)
    }

    @AfterEach
    fun cleanup() {
        databaseService.stop()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a player can be found by their identity id`() {
        val minecraftId = Uuid.random()
        val created = service.upsert(minecraftId, "Alice")
        assertNotEquals(minecraftId.toJavaUuid(), created.id)

        val found = service.getPlayerDetailsById(created.id.let { Uuid.parse(it.toString()) })

        assertEquals("Alice", found?.name)
        assertEquals(created.id, found?.id)
    }

    @Test
    fun `the minecraft uuid is not an identity id`() {
        val minecraftId = Uuid.random()
        service.upsert(minecraftId, "Alice")

        assertNull(service.getPlayerDetailsById(minecraftId))
    }

    @Test
    fun `a player can be found by their minecraft uuid and name`() {
        val minecraftId = Uuid.random()
        val created = service.upsert(minecraftId, "Alice")

        assertEquals(created.id, service.getPlayerDetailsByPlayerId(minecraftId)?.id)
        assertEquals(created.id, service.getPlayerDetailsByName("Alice")?.id)
    }

    @Test
    fun `names are looked up case-insensitively`() {
        val created = service.upsert(Uuid.random(), "Alice")

        assertEquals(created.id, service.getPlayerDetailsByName("alice")?.id)
        assertEquals(created.id, service.getPlayerDetailsByName("ALICE")?.id)
        assertNull(service.getPlayerDetailsByName("Alic"))
    }

    @Test
    fun `when a name belonged to several players the one that joined last wins`() {
        service.upsert(Uuid.random(), "Steve")
        Thread.sleep(5)
        val newest = service.upsert(Uuid.random(), "Steve")

        assertEquals(newest.id, service.getPlayerDetailsByName("Steve")?.id)
    }

    @Test
    fun `players can be fetched by several identity ids at once`() {
        val alice = service.upsert(Uuid.random(), "Alice")
        val bob = service.upsert(Uuid.random(), "Bob")
        service.upsert(Uuid.random(), "Carol")

        val found = service.getPlayerDetailsByIds(listOf(alice.id, bob.id, java.util.UUID.randomUUID()).map { Uuid.parse(it.toString()) })

        assertEquals(setOf("Alice", "Bob"), found.map { it.name }.toSet())
        assertEquals(emptyList<Any>(), service.getPlayerDetailsByIds(emptyList()))
    }

    @Test
    fun `names can be searched by prefix, case-insensitively and sorted`() {
        listOf("Alice", "alfred", "Bob", "a_b", "axb").forEach { service.upsert(Uuid.random(), it) }

        assertEquals(listOf("alfred", "Alice"), service.getPlayerNamesByPrefix("al", 10))
        assertEquals(listOf("alfred", "Alice"), service.getPlayerNamesByPrefix("AL", 10))
        assertEquals(listOf("a_b", "alfred", "Alice", "axb"), service.getPlayerNamesByPrefix("a", 10))
        assertEquals(listOf("a_b", "alfred", "Alice", "axb", "Bob"), service.getPlayerNamesByPrefix("", 10))
    }

    @Test
    fun `an underscore in a prefix is not a wildcard`() {
        listOf("a_b", "axb").forEach { service.upsert(Uuid.random(), it) }

        assertEquals(listOf("a_b"), service.getPlayerNamesByPrefix("a_", 10))
    }

    @Test
    fun `the name limit is respected`() {
        listOf("Alice", "alfred", "Bob").forEach { service.upsert(Uuid.random(), it) }

        assertEquals(2, service.getPlayerNamesByPrefix("", 2).size)
    }

    @Test
    fun `all players are paginated by name`() {
        listOf("Carol", "alice", "Bob").forEach { service.upsert(Uuid.random(), it) }

        assertEquals(3, service.getTotalPlayerCount())
        assertEquals(listOf("alice", "Bob"), service.getAllPlayerDetails(page = 1, itemsPerPage = 2).map { it.name })
        assertEquals(1, service.getAllPlayerDetails(page = 2, itemsPerPage = 2).size)
    }
}
