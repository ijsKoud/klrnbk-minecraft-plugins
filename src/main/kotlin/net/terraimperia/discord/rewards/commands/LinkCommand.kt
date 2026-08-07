package net.terraimperia.discord.rewards.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.terraimperia.discord.rewards.facade.AdminFacade
import net.terraimperia.discord.rewards.facade.LinkFacade
import net.terraimperia.discord.rewards.services.minimessage.MiniMessageService
import java.util.concurrent.CompletableFuture

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
                    .requires { source -> source.hasPermission("terra-imperia.link.discord.use") }
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
