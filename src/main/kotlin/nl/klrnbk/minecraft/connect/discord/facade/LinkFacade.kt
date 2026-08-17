package nl.klrnbk.minecraft.connect.discord.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService
import nl.klrnbk.minecraft.connect.discord.services.link.LinkService
import nl.klrnbk.minecraft.connect.discord.services.minimessage.MiniMessageService
import nl.klrnbk.minecraft.connect.discord.utils.formatInstantToDateTimeString
import kotlin.time.Clock

@Singleton
class LinkFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val miniMessageService: MiniMessageService,
        private val linkService: LinkService,
    ) {
        fun hasPermission(source: CommandSource): Boolean {
            if (source !is Player) {
                val message = miniMessageService.deserialize(configService.config.messages.playerOnlyCommand)
                source.sendMessage(message)

                return false
            }

            return true
        }

        fun status(context: CommandContext<CommandSource>): Int {
            val yesString = configService.config.messages.yes
            val noString = configService.config.messages.no
            val undefinedString = configService.config.messages.undefined

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

        fun unlink(context: CommandContext<CommandSource>): Int {
            val player = context.source as Player
            val id = player.uniqueId
            val linkDetails = linkService.getStatus(id)

            if (!linkDetails.isLinked) {
                val message = miniMessageService.deserialize(configService.config.messages.notLinked)
                context.source.sendMessage(message)
                return 0
            }

            val playersNextUnlinkDate = linkService.getPlayersNextUnlinkDate(id, configService.config.registration.resetLimit)
            if (playersNextUnlinkDate != null && playersNextUnlinkDate > Clock.System.now()) {
                val message =
                    miniMessageService.deserialize(
                        configService.config.messages.unlinkTooSoon,
                        Placeholder.unparsed("date", formatInstantToDateTimeString(playersNextUnlinkDate)),
                    )
                context.source.sendMessage(message)
                return 0
            }

            linkService.unlink(id)
            val message = miniMessageService.deserialize(configService.config.messages.unlinked)
            context.source.sendMessage(message)
            return 1
        }
    }
