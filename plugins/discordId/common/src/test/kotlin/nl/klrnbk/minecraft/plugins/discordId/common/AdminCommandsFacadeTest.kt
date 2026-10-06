package nl.klrnbk.minecraft.plugins.discordId.common

import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.uuid.Uuid

class AdminCommandsFacadeTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }

    init {
        registerIdentity(identityPlayer("Alice"))
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private fun exports() = env.dataDirectory.resolve("exports")

    @Test
    fun `exporting writes a file to the exports folder and reports the row count`() {
        env.linkRepository.create(Uuid.random(), "111", "alice#1", false)
        env.linkRepository.create(Uuid.random(), "222", "bob#1", true)

        val message = env.adminCommandsFacade.exportData(env.dataDirectory)

        assertEquals(LanguageKeys.LINK_CODE_EXPORT_SUCCESS, message.messageKey())
        val (fileName, rows) = message.messageArguments().map { (it as TextComponent).content() }
        assertTrue(Files.isRegularFile(exports().resolve(fileName)))
        assertEquals("2", rows)
    }

    @Test
    fun `an export can be imported into an empty database`() {
        val identityId = Uuid.random()
        env.linkRepository.create(identityId, "111", "alice#1", true)
        val codeOwner = Uuid.random()
        env.codeRepository.create(codeOwner)
        val fileName = (env.adminCommandsFacade.exportData(env.dataDirectory).messageArguments().first() as TextComponent).content()

        // Restore into freshly created, empty tables.
        env.databaseService.stop()
        env.databaseService.start(env.datasourceConfig, env.dataDirectory.resolve("restored").also { Files.createDirectories(it) })
        Files.createDirectories(env.dataDirectory.resolve("restored").resolve("exports"))
        Files.copy(exports().resolve(fileName), env.dataDirectory.resolve("restored").resolve("exports").resolve(fileName))

        val message = env.adminCommandsFacade.importData(env.dataDirectory.resolve("restored"), fileName)

        assertEquals(LanguageKeys.LINK_CODE_IMPORT_SUCCESS, message.messageKey())
        // Link codes are short-lived and not part of an export, only the links are.
        assertEquals("1", (message.messageArguments().single() as TextComponent).content())
        assertEquals("111", env.linkRepository.findByIdentityId(identityId)?.discordId)
        assertNull(env.codeRepository.findByEntityId(codeOwner))
    }

    @Test
    fun `importing a file that does not exist reports the failure`() {
        val message = env.adminCommandsFacade.importData(env.dataDirectory, "missing.zip")

        assertEquals(LanguageKeys.LINK_CODE_TRANSFER_FAILED, message.messageKey())
        assertTrue((message.messageArguments().single() as TextComponent).content().contains("missing.zip"))
    }

    @Test
    fun `importing with a path is rejected so files outside the exports folder can't be read`() {
        val message = env.adminCommandsFacade.importData(env.dataDirectory, "../config.yml")

        assertEquals(LanguageKeys.LINK_CODE_TRANSFER_FAILED, message.messageKey())
    }

    @Test
    fun `importing into tables that already hold rows is refused`() {
        env.linkRepository.create(Uuid.random(), "111", "alice#1", false)
        val fileName = (env.adminCommandsFacade.exportData(env.dataDirectory).messageArguments().first() as TextComponent).content()

        val message = env.adminCommandsFacade.importData(env.dataDirectory, fileName)

        assertEquals(LanguageKeys.LINK_CODE_TRANSFER_FAILED, message.messageKey())
        assertEquals(1, env.linkRepository.findAll().size)
    }

    @Test
    fun `export suggestions list the zip files in the exports folder`() {
        assertTrue(env.dataTransferService.getExportSuggestions(env.dataDirectory).isEmpty())
        val fileName = (env.adminCommandsFacade.exportData(env.dataDirectory).messageArguments().first() as TextComponent).content()
        Files.writeString(exports().resolve("notes.txt"), "not an export")

        assertEquals(listOf(fileName), env.dataTransferService.getExportSuggestions(env.dataDirectory))
    }

    @Test
    fun `the transfer service rejects paths itself`() {
        assertThrows(DatabaseTransferException::class.java) { env.dataTransferService.importData(env.dataDirectory, "../x.zip") }
        assertFalse(Files.exists(env.dataDirectory.resolve("x.zip")))
    }
}
