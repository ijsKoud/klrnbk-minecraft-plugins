package nl.klrnbk.minecraft.connect.discord.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import nl.klrnbk.minecraft.connect.discord.facade.LinkFacade

@Singleton
class LinkCommand
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
    ) {
        fun command(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("link")
                    .requires { source -> source.hasPermission("klrnbk.connect.discord.link.use") && linkFacade.hasPermission(source) }
                    .executes(linkFacade::register)
                    .then(statusCommand())
                    .build()

            return BrigadierCommand(commandNode)
        }

        private fun statusCommand(): LiteralCommandNode<CommandSource> =
            BrigadierCommand
                .literalArgumentBuilder("status")
                .executes(linkFacade::status)
                .build()
    }
