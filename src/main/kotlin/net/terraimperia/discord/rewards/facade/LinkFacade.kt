package net.terraimperia.discord.rewards.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.terraimperia.discord.rewards.services.config.ConfigService
import net.terraimperia.discord.rewards.services.link.LinkService
import net.terraimperia.discord.rewards.services.minimessage.MiniMessageService

@Singleton
class LinkFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val miniMessageService: MiniMessageService,
        private val linkService: LinkService,
    ) {
        fun status(context: CommandContext<CommandSource>): Int {
            val yesString = configService.config.messages.yes
            val noString = configService.config.messages.no
            val undefinedString = configService.config.messages.undefined

            if (context.source !is Player) {
                val message = miniMessageService.deserialize(configService.config.messages.playerOnlyCommand)
                context.source.sendMessage(message)
                return 0
            }

            val id = (context.source as Player).uniqueId
            val linkDetails = linkService.getStatus(id)
            val message =
                miniMessageService.deserialize(
                    configService.config.messages.infoMessage,
                    Placeholder.parsed("link-status", if (linkDetails.isLinked) yesString else noString),
                    Placeholder.parsed("last-registry-date", linkDetails.lastLinkDate ?: undefinedString),
                    Placeholder.parsed("linked-discord-id", linkDetails.discordId ?: undefinedString),
                )

            context.source.sendMessage(message)
            return 1
        }

        fun register(context: CommandContext<CommandSource>): Int {
            if (context.source !is Player) {
                val message = miniMessageService.deserialize(configService.config.messages.playerOnlyCommand)
                context.source.sendMessage(message)
                return 0
            }

            val player = context.source as Player
            val id = player.uniqueId
            val linkDetails = linkService.getStatus(id)

            if (linkDetails.isLinked) {
                val message = miniMessageService.deserialize(configService.config.messages.alreadyLinked)
                context.source.sendMessage(message)
                return 0
            }

            val linkCode = linkService.createRegistrationCode(id)
            val message =
                miniMessageService.deserialize(
                    configService.config.messages.registrationCode,
                    Placeholder.parsed("link-code", linkCode),
                )
            context.source.sendMessage(message)
            return 1
        }
    }
