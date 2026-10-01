package nl.klrnbk.minecraft.plugins.identity.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerInformationCommandFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory

@Singleton
class PlayerInformationCommand
    @Inject
    constructor(
        private val playerInformationCommandFacade: PlayerInformationCommandFacade,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("player")
                    .requires { source -> source.hasPermission(Permissions.VIEW_PLAYER_INFO) }
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("player", StringArgumentType.string())
                            .suggests { _, builder ->
                                playerInformationCommandFacade.getPlayerNameSuggestions().forEach(builder::suggest)
                                builder.buildFuture()
                            }.executes(::execute)
                            .build(),
                    ).build()

            return BrigadierCommand(commandNode)
        }

        override fun meta(
            manager: CommandManager,
            plugin: Any,
        ): CommandMeta =
            manager
                .metaBuilder(configure())
                .aliases("identity-player")
                .plugin(plugin)
                .build()

        private fun execute(source: CommandContext<CommandSource>): Int {
            val playerName = source.getArgument("player", String::class.java)
            val player = playerInformationCommandFacade.getPlayerInformation(playerName)
            if (player == null) {
                val message =
                    MessageFactory
                        .factory()
                        .appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_NOT_FOUND)
                        .build()

                source.source.sendMessage(message)
                return 1
            }

            val message = playerInformationCommandFacade.produceMessage(player)
            source.source.sendMessage(message)

            return 0
        }
    }
