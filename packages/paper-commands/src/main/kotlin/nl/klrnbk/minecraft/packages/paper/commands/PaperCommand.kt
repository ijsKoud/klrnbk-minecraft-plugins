package nl.klrnbk.minecraft.packages.paper.commands

import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.plugin.java.JavaPlugin

/**
 * Base class for a Paper command — the Paper-side equivalent of
 * `packages:velocity-commands`'s `Command` interface. Registers itself
 * against the server's command map (see [PaperCommandRegistrar]) instead of
 * requiring a `plugin.yml` `commands:` block, so commands can be added
 * purely in Kotlin and picked up by Guice like everything else.
 */
abstract class PaperCommand(
    private val name: String,
    private val aliases: List<String> = emptyList(),
    private val permission: String? = null,
) : TabExecutor {
    fun register(plugin: JavaPlugin) {
        PaperCommandRegistrar.register(
            plugin = plugin,
            name = name,
            aliases = aliases,
            permission = permission,
            executor = this,
        )
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<out String>,
    ): List<String> = emptyList()
}
