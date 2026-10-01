package nl.klrnbk.minecraft.plugins.pkgs.i18n

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Reads per-locale MiniMessage YAML language files (e.g. `en_us.yml`,
 * `nl_nl.yml`) into a flat key -> value map per locale — either from a
 * real filesystem directory ([loadDirectory], for server-admin-editable
 * files in a plugin's data folder) or from the classpath ([loadResource]/
 * [loadResources], for a plugin's own bundled default translations under
 * `src/main/resources`).
 *
 * Parsing is plain Jackson (`jackson-dataformat-yaml`) — this class has no
 * Bukkit dependency at all, unlike the rest of this package (which needs
 * Bukkit types for items and Player locales). That's deliberate: loading
 * and flattening language files is genuinely platform-agnostic logic.
 *
 * Each file's name (minus extension) is parsed as the locale it provides,
 * using Minecraft's own underscore convention (`en_us`, `nl_nl`) rather
 * than BCP-47 hyphens. Values can be:
 * - a plain string — a single MiniMessage-formatted line (item/GUI names,
 *   single-line messages)
 * - a list of strings — multiple MiniMessage-formatted lines (item lore,
 *   multi-line messages)
 *
 * Nested YAML sections are flattened into dot-separated keys, so
 * ```yaml
 * item:
 *   sword:
 *     name: "<red>Sword of Flame"
 *     lore:
 *       - "<gray>A blade wreathed in fire."
 *       - "<gray>Deals <gold><damage></gold> damage."
 * ```
 * becomes the keys `item.sword.name` (a String) and `item.sword.lore` (a
 * `List<String>`).
 */
object LanguageFileLoader {

    private val yamlMapper = ObjectMapper(YAMLFactory())

    fun loadDirectory(directory: File): Map<Locale, Map<String, Any>> {
        if (!directory.isDirectory) return emptyMap()

        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("yml", ignoreCase = true)
        } ?: emptyArray()

        val result = mutableMapOf<Locale, Map<String, Any>>()
        for (file in files) {
            val locale = parseLocale(file.nameWithoutExtension) ?: continue
            val parsed = file.inputStream().use(::readYaml)
            val flattened = mutableMapOf<String, Any>()
            flatten(parsed, "", flattened)
            result[locale] = flattened
        }
        return result
    }

    /**
     * Loads a single language file bundled as a classpath resource (a
     * plugin's own `src/main/resources/lang/en_us.yml`, once built into
     * the jar) rather than a real filesystem file. [resourcePath] is
     * resolved via [classLoader] the same way `JavaPlugin#getResource`
     * does — e.g. `"lang/en_us.yml"`. Returns null if the resource
     * doesn't exist, or its filename doesn't parse as a locale.
     */
    fun loadResource(classLoader: ClassLoader, resourcePath: String): Pair<Locale, Map<String, Any>>? {
        val locale = parseLocale(File(resourcePath).nameWithoutExtension) ?: return null
        val stream = classLoader.getResourceAsStream(resourcePath) ?: return null

        val parsed = stream.use(::readYaml)
        val flattened = mutableMapOf<String, Any>()
        flatten(parsed, "", flattened)
        return locale to flattened
    }

    /**
     * Loads every resource path in [resourcePaths] the same way as
     * [loadResource]. Paths whose resource doesn't exist, or whose
     * filename doesn't parse as a locale, are silently skipped — there's
     * no directory listing to fall back on for classpath resources, so
     * (unlike [loadDirectory]) the caller has to know which paths to ask
     * for.
     */
    fun loadResources(classLoader: ClassLoader, resourcePaths: Collection<String>): Map<Locale, Map<String, Any>> {
        val result = mutableMapOf<Locale, Map<String, Any>>()
        for (path in resourcePaths) {
            val (locale, entries) = loadResource(classLoader, path) ?: continue
            result[locale] = entries
        }
        return result
    }

    private fun readYaml(input: InputStream): Map<*, *> =
        yamlMapper.readValue(input, Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun flatten(section: Map<*, *>, prefix: String, out: MutableMap<String, Any>) {
        for ((rawKey, value) in section) {
            val key = rawKey.toString()
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is Map<*, *> -> flatten(value, path, out)
                is String -> out[path] = value
                is List<*> -> {
                    val strings = value.filterIsInstance<String>()
                    if (strings.isNotEmpty()) out[path] = strings
                }
                else -> Unit // ignore anything that isn't text (numbers, booleans, etc.)
            }
        }
    }

    /** Parses Minecraft-style locale filenames like "en_us" or "nl_nl" —
     * returns null (skipping the file) for anything unparseable. */
    private fun parseLocale(fileName: String): Locale? {
        val parts = fileName.split("_")
        val locale = when (parts.size) {
            1 -> Locale.forLanguageTag(parts[0])
            2 -> Locale.of(parts[0], parts[1].uppercase())
            else -> return null
        }
        return locale.takeIf { it.language.isNotEmpty() }
    }
}
