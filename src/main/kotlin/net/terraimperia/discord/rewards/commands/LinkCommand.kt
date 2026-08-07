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
                    .executes(linkFacade::register)
                    .requires { source -> source.hasPermission("terra-imperia.link.use") }
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
