package nl.klrnbk.minecraft.plugins.identity.common

import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransfer
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import kotlin.uuid.Uuid

class DataTransferTest {
    private val source = IdentityTestEnvironment()
    private val target = IdentityTestEnvironment()

    private fun service(environment: IdentityTestEnvironment) =
        DataTransferService(NOPLogger.NOP_LOGGER, DatabaseTransfer(environment.context))

    @AfterEach
    fun cleanup() {
        source.close()
        target.close()
    }

    @Test
    fun `players exported from one database can be imported into another`() {
        val players = PlayerEntityRepository(source.context)
        val ids = List(3) { Uuid.random() }
        ids.forEachIndexed { index, id -> players.create(id, "Player$index") }

        val exported = service(source).exportData(source.directory)
        Files.createDirectories(target.directory.resolve("exports"))
        Files.copy(source.directory.resolve("exports").resolve(exported.fileName), target.directory.resolve("exports").resolve(exported.fileName))
        val imported = service(target).importData(target.directory, exported.fileName)

        assertEquals(3L, imported.rows.getValue("players"))
        val restored = PlayerEntityRepository(target.context)
        assertEquals(ids.toSet(), ids.mapNotNull { restored.findByPlayerId(it)?.playerId }.toSet())
        assertEquals(listOf(exported.fileName), service(target).getExportSuggestions(target.directory))
    }

    @Test
    fun `import only reads files from the exports folder`() {
        val exception =
            assertThrows(DatabaseTransferException::class.java) { service(target).importData(target.directory, "../config.yml") }

        assertEquals("Give the name of a file in the exports folder, not a path.", exception.message)
    }

    @Test
    fun `importing a file that does not exist is reported`() {
        assertThrows(DatabaseTransferException::class.java) { service(target).importData(target.directory, "missing.zip") }
    }
}
