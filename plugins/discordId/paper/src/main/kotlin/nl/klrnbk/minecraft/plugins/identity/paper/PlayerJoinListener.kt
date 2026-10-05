package nl.klrnbk.minecraft.plugins.identity.paper

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(
    private val plugin: MCPluginMain,
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (plugin.config.debug) {
            event.player.sendMessage(Component.text("MCPlugin is running in debug mode."))
        }
    }
}
