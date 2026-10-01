package nl.klrnbk.minecraft.packages.paper.commands

import org.bukkit.Server
import org.bukkit.command.CommandMap
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabExecutor
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

object PaperCommandRegistrar {
    fun register(
        plugin: JavaPlugin,
        name: String,
        aliases: List<String>,
        permission: String?,
        executor: TabExecutor,
    ) {
        val commandMap = getCommandMap(plugin.server)
        val command = createPluginCommand(name, plugin)

        command.setExecutor(executor)
        command.tabCompleter = executor
        command.aliases = aliases
        if (permission != null) {
            command.permission = permission
        }

        commandMap.register(plugin.name.lowercase(), command)
    }

    private fun getCommandMap(server: Server): CommandMap {
        var current: Class<*>? = server.javaClass
        var field: java.lang.reflect.Field? = null
        while (current != null && field == null) {
            field = runCatching { current.getDeclaredField("commandMap") }.getOrNull()
            current = current.superclass
        }

        field ?: throw IllegalStateException("Could not locate the server command map.")

        field.isAccessible = true
        return field.get(server) as CommandMap
    }

    private fun createPluginCommand(
        name: String,
        plugin: JavaPlugin,
    ): PluginCommand {
        val constructor =
            PluginCommand::class.java.getDeclaredConstructor(
                String::class.java,
                Plugin::class.java,
            )
        constructor.isAccessible = true
        return constructor.newInstance(name, plugin)
    }
}
