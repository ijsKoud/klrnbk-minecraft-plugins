package nl.klrnbk.minecraft.plugins.identity.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.common.CHAT_PREFIX
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys.PLAYER_LIST_HEADER
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerlistCommandFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory

@Singleton
class PlayerlistCommand
    @Inject
    constructor(
        private val playerlistCommandFacade: PlayerlistCommandFacade,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("playerlist")
                    .requires { source -> source.hasPermission(Permissions.VIEW_PLAYERS) }
                    .executes(::executeWithoutPageNumber)
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                            .executes(::executeWithPageNumber)
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
                .aliases("identity-playerlist")
                .plugin(plugin)
                .build()

        private fun executeWithoutPageNumber(source: CommandContext<CommandSource>): Int {
            val message = execute(1)
            source.source.sendMessage(message)

            return 0
        }

        private fun executeWithPageNumber(source: CommandContext<CommandSource>): Int {
            val page = source.getArgument("page", Int::class.java)
            val message = execute(page)

            source.source.sendMessage(message)

            return 0
        }

        private fun execute(pageNumber: Int): TextComponent {
            val result = playerlistCommandFacade.getPlayerList(pageNumber)
            val message = playerlistCommandFacade.produceMessage(result)

            return message
        }
    }
