package nl.klrnbk.minecraft.plugins.identity.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerLogsCommandFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

@Singleton
class PlayerLogsCommand
    @Inject
    constructor(
        private val playerLogsCommandFacade: PlayerLogsCommandFacade,
    ) : PaperCommand("playerlogs", listOf("identity-playerlogs"), Permissions.VIEW_LOGS) {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            val playerName = args.firstOrNull() ?: return false
            val page = args.getOrNull(1)?.toIntOrNull() ?: 1
            val canSeePlayerIps = sender.hasPermission(Permissions.VIEW_IPS)

            val message = execute(playerName, page, canSeePlayerIps)
            sender.sendMessage(message)

            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): List<String> =
            if (args.size == 1) {
                playerLogsCommandFacade
                    .getPlayerNameSuggestions()
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            } else {
                emptyList()
            }

        private fun execute(
            playerName: String,
            pageNumber: Int,
            canSeePlayerIps: Boolean,
        ) = playerLogsCommandFacade.getPlayerLogs(playerName, pageNumber)?.let {
            playerLogsCommandFacade.produceMessage(it, canSeePlayerIps)
        } ?: MessageFactory
            .factory()
            .appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_NOT_FOUND)
            .build()
    }
