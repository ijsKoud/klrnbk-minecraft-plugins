package nl.klrnbk.minecraft.plugins.identity.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerlistCommandFacade
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import kotlin.math.max

@Singleton
class PlayerlistCommand
    @Inject
    constructor(
        private val playerlistCommandFacade: PlayerlistCommandFacade,
    ) : PaperCommand("playerlist", listOf("identity-playerlist"), Permissions.VIEW_PLAYERS) {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            val page = args.firstOrNull()?.toIntOrNull() ?: 1
            val message = execute(page)
            sender.sendMessage(message)

            return true
        }

        private fun execute(pageNumber: Int): TextComponent {
            val result = playerlistCommandFacade.getPlayerList(max(pageNumber, 1))
            return playerlistCommandFacade.produceMessage(result)
        }
    }
