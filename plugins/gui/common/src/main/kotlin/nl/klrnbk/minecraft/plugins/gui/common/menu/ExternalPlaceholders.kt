package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Placeholders from other plugins, for YAML menu names and lore. PlaceholderAPI itself is a Bukkit plugin and does not
 * exist on Velocity; there are two proxy-side routes and both are supported when their plugin is installed:
 *
 * * **MiniPlaceholders** (`<luckperms_prefix>` style MiniMessage tags) — native to Velocity, synchronous.
 * * **PAPIProxyBridge** (`%vault_eco_balance%` style PlaceholderAPI syntax) — asks the player's *backend* server over
 *   plugin messages, so it is asynchronous: see [PapiPlaceholders].
 *
 * Neither is a compile-time dependency (reflection), and neither is required.
 */
class ExternalPlaceholders(
    private val mini: MiniPlaceholdersBridge? = null,
    private val papi: PapiPlaceholders? = null,
) {
    private val miniMessage = MiniMessage.miniMessage()

    /** True if [text] may resolve differently per viewer or over time, so the item has to be rendered per viewer. */
    fun needsPerViewer(text: String): Boolean = (papi != null && PapiPlaceholders.PATTERN.containsMatchIn(text)) || (mini?.available == true && '<' in text)

    /** Renders MiniMessage [text] for [player]: PlaceholderAPI values first, then MiniPlaceholders tags. */
    fun render(
        text: String,
        player: Player?,
    ): Component {
        var source = text
        if (player != null && papi != null && PapiPlaceholders.PATTERN.containsMatchIn(source)) source = papi.format(player, source)
        val resolver = if (player != null) mini?.resolver() else null
        return if (resolver != null && player != null) miniMessage.deserialize(source, player, resolver) else miniMessage.deserialize(source)
    }

    fun forget(player: UUID) {
        papi?.forget(player)
    }

    companion object {
        val NONE = ExternalPlaceholders()
    }
}

/** MiniPlaceholders' `audienceGlobalPlaceholders()` resolver, fetched by reflection on every use because expansions register late. */
class MiniPlaceholdersBridge(
    private val classLoader: ClassLoader = MiniPlaceholdersBridge::class.java.classLoader,
) {
    val available: Boolean get() = type() != null

    private fun type(): Class<*>? = runCatching { Class.forName("io.github.miniplaceholders.api.MiniPlaceholders", true, classLoader) }.getOrNull()

    fun resolver(): TagResolver? = runCatching { type()?.getMethod("audienceGlobalPlaceholders")?.invoke(null) as? TagResolver }.getOrNull()
}

/** Formats text with PlaceholderAPI on the player's backend server. */
fun interface PapiFormatter {
    fun format(
        player: UUID,
        text: String,
    ): CompletableFuture<String>
}

/** PAPIProxyBridge's `PlaceholderAPI.createInstance().formatPlaceholders(text, uuid)`, by reflection. */
class PapiProxyBridgeFormatter(
    classLoader: ClassLoader = PapiProxyBridgeFormatter::class.java.classLoader,
) : PapiFormatter {
    private val api: Any? =
        runCatching {
            Class.forName("net.william278.papiproxybridge.api.PlaceholderAPI", true, classLoader).getMethod("createInstance").invoke(null)
        }.getOrNull()
    private val method = api?.javaClass?.getMethod("formatPlaceholders", String::class.java, UUID::class.java)

    val available: Boolean get() = api != null && method != null

    @Suppress("UNCHECKED_CAST")
    override fun format(
        player: UUID,
        text: String,
    ): CompletableFuture<String> = method!!.invoke(api, text, player) as CompletableFuture<String>
}

/**
 * Bridges the synchronous "give me the item for this viewer" render to PAPIProxyBridge's asynchronous answers.
 *
 * [format] never blocks: it returns the last known value for that (player, text) — or, before the first answer, the text with
 * its placeholders blanked — and starts a fetch when the value is missing or older than [ttlMillis]. When an answer differs
 * from what was shown, [onChanged] is called so the viewer's menu re-renders. Consequences worth knowing: the first render of a
 * menu shows blanks for a moment; values only update when something re-renders the menu (`refresh_seconds`, a click). Values
 * are additionally cached by PAPIProxyBridge itself (30 s by default).
 */
class PapiPlaceholders(
    private val formatter: PapiFormatter,
    private val logger: Logger,
    private val onChanged: (Player) -> Unit,
    private val ttlMillis: Long = 5_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(
        val value: String,
        val at: Long,
    )

    private val cache = ConcurrentHashMap<Pair<UUID, String>, Entry>()
    private val inFlight = ConcurrentHashMap.newKeySet<Pair<UUID, String>>()
    private var warned = false

    fun format(
        player: Player,
        text: String,
    ): String {
        val key = player.uniqueId to text
        val entry = cache[key]
        if (entry == null || clock() - entry.at > ttlMillis) fetch(player, key, text)
        return entry?.value ?: PATTERN.replace(text, "")
    }

    private fun fetch(
        player: Player,
        key: Pair<UUID, String>,
        text: String,
    ) {
        if (!inFlight.add(key)) return
        try {
            formatter.format(player.uniqueId, text).whenComplete { result, error ->
                inFlight.remove(key)
                if (error != null || result == null) {
                    if (!warned) {
                        warned = true
                        logger.warn("PAPIProxyBridge could not resolve placeholders for {} (is PlaceholderAPI + PAPIProxyBridge on the player's backend?): {}", player.username, error?.toString())
                    }
                    return@whenComplete
                }
                val previous = cache.put(key, Entry(LegacyColors.toMiniMessage(result), clock()))
                if (previous?.value != LegacyColors.toMiniMessage(result)) onChanged(player)
            }
        } catch (e: Exception) {
            inFlight.remove(key)
            logger.warn("PAPIProxyBridge request failed: {}", e.toString())
        }
    }

    fun forget(player: UUID) {
        cache.keys.removeIf { it.first == player }
        inFlight.removeIf { it.first == player }
    }

    val cachedEntries: Int get() = cache.size

    companion object {
        /** `%name%`, `%name_with_args%`. A lone `%` (as in "100%") does not match. */
        val PATTERN = Regex("%[A-Za-z0-9_.:@-]+%")
    }
}

/** PlaceholderAPI expansions usually answer with legacy `§` codes; menus are MiniMessage, so translate them. */
object LegacyColors {
    private val codes =
        mapOf(
            '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua", '4' to "dark_red", '5' to "dark_purple",
            '6' to "gold", '7' to "gray", '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua", 'c' to "red",
            'd' to "light_purple", 'e' to "yellow", 'f' to "white", 'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough",
            'n' to "underlined", 'o' to "italic", 'r' to "reset",
        )
    private val hex = Regex("§x(?:§[0-9a-fA-F]){6}")

    fun toMiniMessage(text: String): String {
        if ('§' !in text) return text
        val withHex =
            hex.replace(text) { match ->
                "<#" + match.value.filter { it != '§' && it != 'x' }.lowercase() + ">"
            }
        val out = StringBuilder()
        var i = 0
        while (i < withHex.length) {
            val c = withHex[i]
            val name = if (c == '§' && i + 1 < withHex.length) codes[withHex[i + 1].lowercaseChar()] else null
            if (name != null) {
                out.append('<').append(name).append('>')
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
