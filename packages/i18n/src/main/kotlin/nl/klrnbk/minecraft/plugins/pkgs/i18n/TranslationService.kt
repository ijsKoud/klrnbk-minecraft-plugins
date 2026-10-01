package nl.klrnbk.minecraft.plugins.pkgs.i18n

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.translation.GlobalTranslator
import java.io.File
import java.util.Locale

/**
 * Loads MiniMessage-formatted language files — from a real filesystem
 * directory ([loadDirectory], typically a plugin's data folder, so
 * server admins can edit/add translations) and/or from the plugin's own
 * bundled classpath resources ([loadResources], typically the plugin's
 * own shipped defaults under `src/main/resources/lang/`) — and makes them
 * available two ways:
 *
 * 1. Registered into Adventure's [GlobalTranslator], so any
 *    `Component.translatable(key)` sent to a player automatically renders
 *    in that player's own client locale, no extra code needed at the send
 *    site. This covers chat, entity display names, scoreboards, the tab
 *    list, and similar.
 * 2. Directly, via [component]/[lore] on this class — for anywhere you
 *    need an already-rendered [Component] right now, with per-call
 *    dynamic placeholders (a player's name, an item's stat roll, etc.).
 *
 * **[component]/[lore] are the ones to use for item display names and
 * lore specifically — not [GlobalTranslator].** Per Paper's own docs,
 * translatable components work "anywhere the component API exists, except
 * for ItemStack display text like the display name or lore" — items have
 * no per-viewer automatic resolution at all, so their text has to be
 * baked into a concrete Component for a specific locale at the point you
 * build the item. If you need an item to show different text to
 * different players, you need a different `ItemStack` per locale (or per
 * player) — see `ItemTranslations.kt` for helpers that do exactly this.
 *
 * Loading both bundled defaults and data-folder overrides together is a
 * common pattern — call [loadResources] first (for the shipped defaults),
 * then [loadDirectory] (for admin overrides), since a later load replaces
 * any earlier entry for the same locale + key.
 *
 * One instance per plugin. Give it a unique [Key] (e.g.
 * `Key.key("myplugin", "translations")`) so its [GlobalTranslator]
 * registration doesn't collide with another plugin's.
 *
 * **Every translation key must start with the [Key]'s namespace plus a
 * dot** (`myplugin.` for the example above — in a language file, wrap
 * everything in a top-level `myplugin:` section). The [Key] only names
 * this service's store; [GlobalTranslator] is one list shared by every
 * plugin on the server and answers a `Component.translatable(key)` with
 * the first store that has that key, so two plugins both defining e.g.
 * `admin.reload.success` would silently show each other's text. Loading
 * a language file with keys outside the namespace fails with an
 * [IllegalArgumentException] instead.
 */
class TranslationService(
    private val name: Key,
    private val miniMessage: MiniMessage = MiniMessage.miniMessage(),
    /** Used when a key is missing for the requested locale, and when the
     * requested locale itself has no entries at all. */
    private val fallbackLocale: Locale = Locale.US,
) {
    private val store = MiniMessageTranslationStore.create(name)
    private val raw = mutableMapOf<Locale, Map<String, Any>>()

    init {
        GlobalTranslator.translator().addSource(store)
    }

    /**
     * Loads every `*.yml` file in [directory] (see
     * [LanguageFileLoader.loadDirectory] for the expected format and
     * filename convention) and registers all of it. Safe to call again
     * later (e.g. from a `/reload`-style command) — re-loading replaces
     * any previously loaded entries for the same locale + key.
     */
    fun loadDirectory(directory: File) {
        mergeAndRegister(LanguageFileLoader.loadDirectory(directory))
    }

    /**
     * Loads language files bundled as classpath resources — a plugin's
     * own `src/main/resources/lang/en_us.yml` etc., once built into the
     * jar. [resourcePaths] must be listed explicitly (e.g.
     * `listOf("lang/en_us.yml", "lang/nl_nl.yml")`) — unlike
     * [loadDirectory], there's no way to "list" what's bundled inside a
     * jar without knowing the paths already.
     */
    fun loadResources(
        classLoader: ClassLoader,
        resourcePaths: Collection<String>,
    ) {
        mergeAndRegister(LanguageFileLoader.loadResources(classLoader, resourcePaths))
    }

    /** Convenience overload — uses [plugin]'s own classloader, so you can
     * call this as `translations.loadResources(this, listOf(...))` from
     * inside a `JavaPlugin`. */
    fun loadResources(
        plugin: Any,
        resourcePaths: Collection<String>,
    ) {
        loadResources(plugin.javaClass.classLoader, resourcePaths)
    }

    private fun mergeAndRegister(loaded: Map<Locale, Map<String, Any>>) {
        requireNamespacedKeys(loaded)

        for ((locale, entries) in loaded) {
            raw.merge(locale, entries) { old, new -> old + new }
            for ((key, value) in entries) {
                when (value) {
                    is String -> {
                        store.register(key, locale, value)
                    }

                    is List<*> -> {
                        // The GlobalTranslator path is single-line only —
                        // join multi-line values (lore, etc.) with a
                        // newline so Component.translatable(key) still
                        // produces something sensible through that path.
                        // Use [lore] instead for anything that actually
                        // needs separate line Components.
                        @Suppress("UNCHECKED_CAST")
                        store.register(key, locale, (value as List<String>).joinToString("\n"))
                    }
                }
            }
        }
    }

    private fun requireNamespacedKeys(loaded: Map<Locale, Map<String, Any>>) {
        val prefix = "${name.namespace()}."
        val misplaced = loaded.flatMap { (locale, entries) -> entries.keys.filterNot { it.startsWith(prefix) }.map { "$it ($locale)" } }
        require(misplaced.isEmpty()) {
            "Translation keys of '${name.asString()}' must start with '$prefix' so they can't collide with other plugins' " +
                "keys in the shared GlobalTranslator, but found: ${misplaced.joinToString()}"
        }
    }

    /**
     * Removes this service's translations from [GlobalTranslator]. Call
     * from `onDisable()` if your plugin supports being unloaded/reloaded
     * without a full server restart — otherwise GlobalTranslator keeps a
     * reference to this store (and therefore this plugin's classloader)
     * forever.
     */
    fun close() {
        GlobalTranslator.translator().removeSource(store)
    }

    /**
     * Renders a single-line translation (item names, GUI titles, one-line
     * messages) for [locale], with optional [resolvers] for dynamic
     * placeholders (`<player>`, `<amount>`, etc. — whatever tags your
     * language files use). Falls back to [fallbackLocale] if [locale] has
     * no entry for [key], then to the raw key itself if even that's
     * missing (so a missing translation is visibly obvious rather than
     * silently blank).
     */
    fun component(
        key: String,
        locale: Locale,
        vararg resolvers: TagResolver,
    ): Component {
        val rawValue = rawStringFor(key, locale) ?: return Component.text(key)
        return miniMessage.deserialize(rawValue, *resolvers)
    }

    /**
     * Renders a multi-line translation (item lore, multi-line messages)
     * as one [Component] per line. A key whose language-file value was a
     * single string (not a list) still works here — returns a
     * single-element list.
     */
    fun lore(
        key: String,
        locale: Locale,
        vararg resolvers: TagResolver,
    ): List<Component> {
        val lines = rawListFor(key, locale) ?: return listOf(Component.text(key))
        return lines.map { miniMessage.deserialize(it, *resolvers) }
    }

    /**
     * True if [key] has a translation for [locale] specifically (does NOT
     * fall back to [fallbackLocale]) — useful for deciding whether to
     * even attempt an optional translation, e.g. lore that's only present
     * for some items.
     */
    fun has(
        key: String,
        locale: Locale,
    ): Boolean = raw[locale]?.containsKey(key) == true

    /** Every locale that has at least one loaded translation. */
    fun availableLocales(): Set<Locale> = raw.keys

    private fun rawStringFor(
        key: String,
        locale: Locale,
    ): String? {
        when (val value = raw[locale]?.get(key)) {
            is String -> return value
            is List<*> -> return value.filterIsInstance<String>().joinToString("\n")
            else -> Unit
        }
        return if (locale != fallbackLocale) rawStringFor(key, fallbackLocale) else null
    }

    private fun rawListFor(
        key: String,
        locale: Locale,
    ): List<String>? {
        when (val value = raw[locale]?.get(key)) {
            is List<*> -> return value.filterIsInstance<String>()
            is String -> return listOf(value)
            else -> Unit
        }
        return if (locale != fallbackLocale) rawListFor(key, fallbackLocale) else null
    }
}
