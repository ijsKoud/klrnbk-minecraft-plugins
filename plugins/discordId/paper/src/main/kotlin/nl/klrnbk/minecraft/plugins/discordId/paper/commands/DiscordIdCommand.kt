package nl.klrnbk.minecraft.plugins.discordId.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.Permissions
import nl.klrnbk.minecraft.plugins.discordId.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.uuid.toKotlinUuid

/**
 * `/discordid <link|unlink|lookup|adminunlink|reload|import|export>`, the Paper counterpart of the Velocity command.
 */
@Singleton
class DiscordIdCommand
    @Inject
    constructor(
        private val adminCommandsFacade: AdminCommandsFacade,
        private val linkFacade: LinkFacade,
        private val dataTransferService: DataTransferService,
        private val plugin: JavaPlugin,
    ) : PaperCommand("discordid", listOf("klrnbk-discordid")) {
        private val subcommands =
            listOf(
                Subcommand("link", Permissions.LINK),
                Subcommand("unlink", Permissions.UNLINK),
                Subcommand("lookup", Permissions.LOOKUP),
                Subcommand("adminunlink", Permissions.UNLINK_FORCED),
                Subcommand("reload", Permissions.ADMIN_RELOAD),
                Subcommand("import", Permissions.ADMIN_IMPORT),
                Subcommand("export", Permissions.ADMIN_EXPORT),
            )

        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            val subcommand = subcommands.firstOrNull { it.name.equals(args.firstOrNull(), ignoreCase = true) } ?: return false
            if (!sender.hasPermission(subcommand.permission)) {
                // Vanilla's own (client translated) message, so no language entry is needed.
                sender.sendMessage(Component.translatable("commands.help.failed"))
                return true
            }

            when (subcommand.name) {
                "link" -> withPlayer(sender) { executeLink(it) }
                "unlink" -> withPlayer(sender) { executeUnlink(it) }
                "lookup" -> sender.sendMessage(linkFacade.lookupPlayer(args.getOrNull(1) ?: return false))
                "adminunlink" -> sender.sendMessage(linkFacade.forceUnlinkPlayer(args.getOrNull(1) ?: return false))
                "reload" -> async(sender) { reload() }
                "export" -> async(sender) { adminCommandsFacade.exportData(plugin.dataFolder.toPath()) }
                "import" -> {
                    val fileName = args.getOrNull(1) ?: return false
                    async(sender) { adminCommandsFacade.importData(plugin.dataFolder.toPath(), fileName) }
                }
            }

            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): List<String> {
            val allowed = subcommands.filter { sender.hasPermission(it.permission) }
            if (args.size == 1) return allowed.map { it.name }.filter { it.startsWith(args[0], ignoreCase = true) }
            if (args.size != 2) return emptyList()

            return when (allowed.firstOrNull { it.name.equals(args[0], ignoreCase = true) }?.name) {
                "lookup", "adminunlink" -> IdentityProvider.get().getPlayerNames(args[1])
                "import" -> dataTransferService.getExportSuggestions(plugin.dataFolder.toPath()).filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
        }

        private fun executeLink(player: Player) {
            player.sendMessage(linkFacade.getLinkCodeForPlayer(player.uniqueId.toKotlinUuid()))
        }

        private fun executeUnlink(player: Player) {
            val isBypassed = player.hasPermission(Permissions.UNLINK_BYPASS)
            player.sendMessage(linkFacade.unlinkPlayer(player.uniqueId.toKotlinUuid(), false, isBypassed))
        }

        private fun reload(): Component {
            adminCommandsFacade.reload(plugin.dataFolder.toPath())
            return MessageFactory
                .factory()
                .appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_RELOAD_SUCCESS)
                .build()
        }

        private inline fun withPlayer(
            sender: CommandSender,
            block: (Player) -> Unit,
        ) {
            if (sender is Player) {
                block(sender)
            } else {
                sender.sendMessage(Component.translatable("permissions.requires.player"))
            }
        }

        /**
         * Reloading and transferring data touches the database and the Discord bot, so it runs off the main thread.
         */
        private fun async(
            sender: CommandSender,
            task: () -> Component,
        ) {
            plugin.server.scheduler.runTaskAsynchronously(
                plugin,
                Runnable {
                    val message =
                        try {
                            task()
                        } catch (exception: Exception) {
                            // Keep the details in the log, not in chat.
                            plugin.logger.log(java.util.logging.Level.SEVERE, "Command failed", exception)
                            Component.text("Unexpected error, see the console.")
                        }

                    sender.sendMessage(message)
                },
            )
        }

        private data class Subcommand(
            val name: String,
            val permission: String,
        )
    }
