package nl.klrnbk.minecraft.plugins.pkgs.i18n

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Locale

class TranslationServiceTest {
    private val directory = Files.createTempDirectory("i18n-test")
    private val services = mutableListOf<TranslationService>()

    @AfterEach
    fun cleanup() {
        services.forEach { it.close() }
        directory.toFile().deleteRecursively()
    }

    private fun text(component: Component) = (component as TextComponent).content()

    private fun service(namespace: String) = TranslationService(Key.key(namespace, "velocity")).also { services.add(it) }

    private fun languageFile(content: String) = directory.resolve("en_us.yml").toFile().apply { writeText(content.trimIndent()) }

    @Test
    fun `keys inside the namespace are loaded`() {
        languageFile(
            """
            whitelist:
              admin:
                reload: "Reloaded Whitelist"
            """,
        )
        val service = service("whitelist")

        service.loadDirectory(directory.toFile())

        assertTrue(service.has("whitelist.admin.reload", Locale.US))
        assertEquals(
            "Reloaded Whitelist",
            text(service.component("whitelist.admin.reload", Locale.US)),
        )
    }

    @Test
    fun `keys outside the namespace are rejected`() {
        languageFile(
            """
            admin:
              reload: "Reloaded"
            whitelist:
              ok: "fine"
            """,
        )

        val exception = assertThrows(IllegalArgumentException::class.java) { service("whitelist").loadDirectory(directory.toFile()) }

        assertTrue(exception.message!!.contains("admin.reload"))
        assertTrue(!exception.message!!.contains("whitelist.ok"))
    }

    @Test
    fun `a key that merely starts with the namespace name is rejected`() {
        languageFile(
            """
            whitelistextra:
              key: "no"
            """,
        )

        assertThrows(IllegalArgumentException::class.java) { service("whitelist").loadDirectory(directory.toFile()) }
    }

    @Test
    fun `two plugins can use the same message name without shadowing each other`() {
        val identity = service("identity")
        val moderation = service("moderation")
        languageFile("identity:\n  reload: \"Identity reloaded\"")
        identity.loadDirectory(directory.toFile())
        languageFile("moderation:\n  reload: \"Moderation reloaded\"")
        moderation.loadDirectory(directory.toFile())

        assertEquals("Identity reloaded", text(identity.component("identity.reload", Locale.US)))
        assertEquals("Moderation reloaded", text(moderation.component("moderation.reload", Locale.US)))
    }
}
