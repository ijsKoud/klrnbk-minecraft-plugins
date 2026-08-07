package net.terraimperia.discord.rewards.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import net.terraimperia.discord.rewards.facade.LinkFacade

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
                    .requires { source -> source.hasPermission("terra-imperia.link.use") && linkFacade.hasPermission(source) }
                    .executes(linkFacade::register)
                    .then(statusCommand())
                    .then(unlinkCommand())
                    .build()

            return BrigadierCommand(commandNode)
        }

        private fun unlinkCommand(): LiteralCommandNode<CommandSource> =
            BrigadierCommand
                .literalArgumentBuilder("unlink")
                .executes(linkFacade::unlink)
                .build()

        private fun statusCommand(): LiteralCommandNode<CommandSource> =
            BrigadierCommand
                .literalArgumentBuilder("status")
                .executes(linkFacade::status)
                .build()
    }
