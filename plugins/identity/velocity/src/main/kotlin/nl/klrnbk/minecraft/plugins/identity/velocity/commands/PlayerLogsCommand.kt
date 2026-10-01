package nl.klrnbk.minecraft.plugins.identity.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerLogsCommandFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory

@Singleton
class PlayerLogsCommand
    @Inject
    constructor(
        private val playerLogsCommandFacade: PlayerLogsCommandFacade,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("playerlogs")
                    .requires { source -> source.hasPermission(Permissions.VIEW_LOGS) }
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("player", StringArgumentType.string())
                            .suggests { _, builder ->
                                playerLogsCommandFacade.getPlayerNameSuggestions().forEach(builder::suggest)
                                builder.buildFuture()
                            }.executes(::executeWithoutPageNumber)
                            .then(
                                BrigadierCommand
                                    .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                                    .executes(::executeWithPageNumber)
                                    .build(),
                            ).build(),
                    ).build()

            return BrigadierCommand(commandNode)
        }

        override fun meta(
            manager: CommandManager,
            plugin: Any,
        ): CommandMeta =
            manager
                .metaBuilder(configure())
                .aliases("identity-playerlogs")
                .plugin(plugin)
                .build()

        private fun executeWithoutPageNumber(source: CommandContext<CommandSource>): Int {
            val playerName = source.getArgument("player", String::class.java)
            val canSeePlayerIps = source.source.hasPermission(Permissions.VIEW_IPS)

            val message = execute(playerName, 1, canSeePlayerIps)
            source.source.sendMessage(message)

            return 0
        }

        private fun executeWithPageNumber(source: CommandContext<CommandSource>): Int {
            val playerName = source.getArgument("player", String::class.java)
            val page = source.getArgument("page", Int::class.java)
            val canSeePlayerIps = source.source.hasPermission(Permissions.VIEW_IPS)

            val message = execute(playerName, page, canSeePlayerIps)
            source.source.sendMessage(message)

            return 0
        }

        private fun execute(
            playerName: String,
            pageNumber: Int,
            canSeePlayerIps: Boolean,
        ): TextComponent {
            val result = playerLogsCommandFacade.getPlayerLogs(playerName, pageNumber)
            if (result == null) {
                val message =
                    MessageFactory
                        .factory()
                        .appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_NOT_FOUND)
                        .build()

                return message
            }

            val message = playerLogsCommandFacade.produceMessage(result, canSeePlayerIps)
            return message
        }
    }
