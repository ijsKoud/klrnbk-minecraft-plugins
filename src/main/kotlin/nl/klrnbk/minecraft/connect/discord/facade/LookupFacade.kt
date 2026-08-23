package nl.klrnbk.minecraft.connect.discord.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService
import nl.klrnbk.minecraft.connect.discord.services.link.LinkService
import nl.klrnbk.minecraft.connect.discord.services.minimessage.MiniMessageService
import nl.klrnbk.minecraft.connect.discord.services.playerdetails.PlayerDetailsService

@Singleton
class LookupFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val miniMessageService: MiniMessageService,
        private val linkService: LinkService,
        private val playerDetailsService: PlayerDetailsService,
    ) {
        fun lookupPlayersDiscord(context: CommandContext<CommandSource>): Int {
            val playerName = context.getArgument("player", String::class.java)
            val playerId = playerDetailsService.getPlayerIdByUsername(playerName)
            if (playerId == null) {
                val message =
                    miniMessageService.deserialize(
                        configService.config.messages.noDiscordLinked,
                    )

                context.source.sendMessage(message)
                return 1
            }

            val discordUsername = linkService.getPlayersDiscordUsername(playerId)
            if (discordUsername == null) {
                val message =
                    miniMessageService.deserialize(
                        configService.config.messages.noDiscordLinked,
                    )

                context.source.sendMessage(message)
                return 1
            }

            val message =
                miniMessageService.deserialize(
                    configService.config.messages.lookupResult,
                    Placeholder.unparsed("minecraft-name", playerName),
                    Placeholder.unparsed("discord-username", discordUsername),
                )

            context.source.sendMessage(message)
            return 0
        }

        fun lookupUsersMinecraftUsername(userId: Long): String? {
            val uuid = linkService.getUsersMinecraftUuid(userId) ?: return null
            return playerDetailsService.getPlayersUsernameById(uuid)
        }
    }
