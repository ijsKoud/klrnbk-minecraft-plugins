package nl.klrnbk.minecraft.plugins.discordId.velocity

import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.Locale

class LanguageFileTest {
    private val service = TranslationService(Key.key("discord-id", "velocity-test"))

    @AfterEach
    fun cleanup() = service.close()

    @Test
    fun `the language file only uses keys inside the plugin namespace and defines every translatable key`() {
        // Throws when a key is outside the "discord-id." namespace.
        service.loadResources(javaClass.classLoader, listOf("lang/en_us.yml"))

        // Some constants are plain texts for Discord replies; only the namespaced ones are translation keys.
        val keys =
            LanguageKeys::class.java.declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .map { it.also { field -> field.isAccessible = true }.get(null) as String }
                .filter { it.startsWith("discord-id.") }

        assertTrue(keys.isNotEmpty())
        val missing = keys.filterNot { service.has(it, Locale.US) }
        assertTrue(missing.isEmpty(), "Keys without a message in lang/en_us.yml: $missing")
    }
}
