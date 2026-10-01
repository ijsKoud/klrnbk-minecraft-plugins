package nl.klrnbk.minecraft.plugins.gui.velocity.commands

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.common.menu.MenuService
import org.slf4j.Logger
import java.time.Duration

/**
 * Everything that turns YAML menus into something a player can use: one command per name a menu lists in `commands:`,
 * the `/klrnbkgui` admin command, and the `refresh_seconds` timers. [sync] is called after every (re)load and
 * replaces the previous registrations, so removing a command from a file removes it from the proxy.
 */
class MenuCommands(
    private val server: ProxyServer,
    private val plugin: Any,
    private val menus: MenuService,
    private val logger: Logger,
) {
    private val registered = mutableSetOf<String>()
    private val timers = mutableListOf<ScheduledTask>()

    /** Registers `/klrnbkgui` once. */
    fun registerAdminCommand() {
        val meta = server.commandManager.metaBuilder(ADMIN_COMMAND).aliases("guimenu").plugin(plugin).build()
        server.commandManager.register(meta, AdminCommand())
    }

    @Synchronized
    fun sync() {
        registered.forEach { server.commandManager.unregister(it) }
        registered.clear()
        timers.forEach { it.cancel() }
        timers.clear()

        for ((id, menu) in menus.menus) {
            for (alias in menu.definition.commands) {
                if (alias in registered || alias == ADMIN_COMMAND || server.commandManager.hasCommand(alias)) {
                    logger.warn("Menu '{}': the command /{} is already taken by another menu or plugin; skipped", id, alias)
                    continue
                }
                val meta = server.commandManager.metaBuilder(alias).plugin(plugin).build()
                server.commandManager.register(meta, OpenMenuCommand(id))
                registered += alias
            }
            menu.definition.refreshSeconds?.let { seconds ->
                timers +=
                    server.scheduler
                        .buildTask(plugin, Runnable { if (menu.gui.viewers.isNotEmpty()) menu.gui.refresh() })
                        .delay(Duration.ofSeconds(seconds.toLong()))
                        .repeat(Duration.ofSeconds(seconds.toLong()))
                        .schedule()
            }
        }
    }

    @Synchronized
    fun shutdown() {
        registered.forEach { server.commandManager.unregister(it) }
        registered.clear()
        timers.forEach { it.cancel() }
        timers.clear()
    }

    private fun describe(result: GuiOpenResult): Component? =
        when (result) {
            GuiOpenResult.OPENED -> null
            GuiOpenResult.NO_PERMISSION -> Component.text("You do not have permission to open this menu.", NamedTextColor.RED)
            GuiOpenResult.UNKNOWN_MENU -> Component.text("That menu does not exist.", NamedTextColor.RED)
            GuiOpenResult.UNSUPPORTED_CLIENT -> Component.text("Your Minecraft version is too old for menus (1.21.5+ required).", NamedTextColor.RED)
            else -> Component.text("The menu could not be opened right now ($result).", NamedTextColor.RED)
        }

    private inner class OpenMenuCommand(
        private val id: String,
    ) : SimpleCommand {
        override fun execute(invocation: SimpleCommand.Invocation) {
            val player = invocation.source() as? Player ?: return invocation.source().sendMessage(Component.text("Players only.", NamedTextColor.RED))
            describe(menus.open(id, player))?.let(player::sendMessage)
        }

        // Permission is checked by the menu itself so the player gets a message rather than "unknown command".
        override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean = true
    }

    /** `/klrnbkgui [list|open <id>|reload]` (alias `/guimenu`). */
    private inner class AdminCommand : SimpleCommand {
        override fun execute(invocation: SimpleCommand.Invocation) {
            val source = invocation.source()
            val args = invocation.arguments()
            when (args.firstOrNull()?.lowercase()) {
                "open" -> {
                    val player = source as? Player ?: return source.sendMessage(Component.text("Players only.", NamedTextColor.RED))
                    val id = args.getOrNull(1)?.lowercase() ?: return source.sendMessage(Component.text("Usage: /$ADMIN_COMMAND open <menu>", NamedTextColor.RED))
                    describe(menus.open(id, player))?.let(source::sendMessage)
                }
                "reload" -> {
                    if (!source.hasPermission(ADMIN_PERMISSION)) return source.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                    val report = menus.reload()
                    sync()
                    source.sendMessage(Component.text("Loaded ${report.loaded.size} menu(s): ${report.loaded.joinToString().ifEmpty { "none" }}", NamedTextColor.GREEN))
                    report.problems.forEach { source.sendMessage(Component.text("! $it", NamedTextColor.RED)) }
                }
                "list", null -> {
                    if (!source.hasPermission(ADMIN_PERMISSION)) return source.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                    source.sendMessage(Component.text("Menus: ${menus.ids.sorted().joinToString().ifEmpty { "none" }}", NamedTextColor.YELLOW))
                }
                else -> source.sendMessage(Component.text("Usage: /$ADMIN_COMMAND [list|open <menu>|reload]", NamedTextColor.RED))
            }
        }

        override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
            val args = invocation.arguments()
            return when {
                args.size <= 1 -> listOf("list", "open", "reload").filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }
                args[0].equals("open", true) -> menus.ids.sorted().filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
        }

        override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean = true
    }

    companion object {
        const val ADMIN_COMMAND = "klrnbkgui"
        const val ADMIN_PERMISSION = "klrnbk.gui.admin"
    }
}

