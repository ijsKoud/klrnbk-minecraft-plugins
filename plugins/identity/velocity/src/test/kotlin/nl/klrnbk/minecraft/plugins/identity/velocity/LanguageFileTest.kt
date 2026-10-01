package nl.klrnbk.minecraft.plugins.identity.velocity

import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.Locale

class LanguageFileTest {
    private val service = TranslationService(Key.key("identity", "velocity-test"))

    @AfterEach
    fun cleanup() = service.close()

    @Test
    fun `language files only use keys inside the plugin namespace and define every language key`() {
        // Throws when a key is outside the "identity." namespace.
        service.loadResources(javaClass.classLoader, listOf("lang/en_us.yml"))

        val keys =
            LanguageKeys::class.java.declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .map { it.also { field -> field.isAccessible = true }.get(null) as String }

        assertTrue(keys.isNotEmpty())
        val missing = keys.filterNot { service.has(it, Locale.US) }
        assertTrue(missing.isEmpty(), "Keys without a message in lang/en_us.yml: $missing")
    }
}
