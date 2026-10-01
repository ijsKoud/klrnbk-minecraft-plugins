package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform

/**
 * `{player}`, `{uuid}`, `{online}`, `{server}`, `{platform}` in item names, lore and action text.
 *
 * Values are substituted into the raw text *before* MiniMessage parsing or command construction. They come from
 * Minecraft account names, UUIDs, a number, registered server names and an enum — none can contain `<`, so they cannot
 * inject formatting, and the command validation of the actions still applies to the result.
 */
object Placeholders {
    private val pattern = Regex("\\{(player|uuid|online|server|platform)}")

    fun containsPlaceholder(text: String): Boolean = pattern.containsMatchIn(text)

    fun substitutor(
        server: ProxyServer,
        player: Player,
        platform: GuiPlatform,
    ): (String) -> String {
        val values =
            mapOf(
                "player" to player.username,
                "uuid" to player.uniqueId.toString(),
                "online" to server.playerCount.toString(),
                "server" to (player.currentServer.map { it.serverInfo.name }.orElse("none")),
                "platform" to platform.name.lowercase(),
            )
        return { text -> pattern.replace(text) { values.getValue(it.groupValues[1]) } }
    }

    /** Placeholders replaced by neutral text, to validate an action at parse time. */
    val validation: (String) -> String = { text -> pattern.replace(text, "x") }
}
