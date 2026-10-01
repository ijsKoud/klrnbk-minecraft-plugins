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
}
