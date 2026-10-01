package nl.klrnbk.minecraft.plugins.identity.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerInformationCommandFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

@Singleton
class PlayerInformationCommand
    @Inject
    constructor(
        private val playerInformationCommandFacade: PlayerInformationCommandFacade,
    ) : PaperCommand("player", listOf("identity-player"), Permissions.VIEW_PLAYER_INFO) {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            val playerName = args.firstOrNull() ?: return false
            val player = playerInformationCommandFacade.getPlayerInformation(playerName)
            if (player == null) {
                val message =
                    MessageFactory
                        .factory()
                        .appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_NOT_FOUND)
                        .build()
                sender.sendMessage(message)
                return true
            }

            sender.sendMessage(playerInformationCommandFacade.produceMessage(player))
            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): List<String> =
            if (args.size == 1) {
                playerInformationCommandFacade
                    .getPlayerNameSuggestions()
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            } else {
                emptyList()
            }
    }
