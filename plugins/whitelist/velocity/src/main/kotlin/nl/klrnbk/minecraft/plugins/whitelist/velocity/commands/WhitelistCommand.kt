package nl.klrnbk.minecraft.plugins.whitelist.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.whitelist.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.whitelist.common.Permissions
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistActionResult
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistCommandFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistLogsCommandFacade
import org.slf4j.Logger
import java.nio.file.Path
import java.util.UUID

/**
 * /whitelist <on|off|add|remove|logs|reload>
 */
@Singleton
class WhitelistCommand
    @Inject
    constructor(
        private val whitelistCommandFacade: WhitelistCommandFacade,
        private val adminCommandsFacade: AdminCommandsFacade,
        private val whitelistLogsCommandFacade: WhitelistLogsCommandFacade,
        private val server: ProxyServer,
        private val logger: Logger,
        @DataDirectory private val dataDirectory: Path,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("whitelist")
                    .requires { source -> PERMISSIONS.any(source::hasPermission) }
                    .then(
                        BrigadierCommand
                            .literalArgumentBuilder("on")
                            .requires { source -> source.hasPermission(Permissions.TOGGLE_WHITELIST) }
                            .executes { context -> toggle(context, enabled = true) },
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("off")
                            .requires { source -> source.hasPermission(Permissions.TOGGLE_WHITELIST) }
                            .executes { context -> toggle(context, enabled = false) },
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("add")
                            .requires { source -> source.hasPermission(Permissions.ADD_PLAYER) }
                            .then(playerArgument(::add)),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("remove")
                            .requires { source -> source.hasPermission(Permissions.REMOVE_PLAYER) }
                            .then(playerArgument(::remove)),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("logs")
                            .requires { source -> source.hasPermission(Permissions.VIEW_LOGS) }
                            .then(
                                BrigadierCommand
                                    .literalArgumentBuilder("settings")
                                    .executes { context -> settingsLogs(context, 1) }
                                    .then(pageArgument { context -> settingsLogs(context, context.getArgument("page", Int::class.java)) }),
                            ).then(
                                BrigadierCommand
                                    .literalArgumentBuilder("player")
                                    .then(
                                        playerArgument { context -> playerLogs(context, 1) }
                                            .then(pageArgument { context -> playerLogs(context, context.getArgument("page", Int::class.java)) }),
                                    ),
                            ),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("reload")
                            .requires { source -> source.hasPermission(Permissions.RELOAD_PLUGIN) }
                            .executes(::reload),
                    ).build()

            return BrigadierCommand(commandNode)
        }

        override fun meta(
            manager: CommandManager,
            plugin: Any,
        ): CommandMeta =
            manager
                .metaBuilder(configure())
                .aliases("klrnbk-whitelist")
                .plugin(plugin)
                .build()

        private fun playerArgument(executor: (CommandContext<CommandSource>) -> Int) =
            BrigadierCommand
                .requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests { _, builder ->
                    server.allPlayers
                        .map { it.username }
                        .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                        .forEach(builder::suggest)
                    builder.buildFuture()
                }.executes(executor)

        private fun pageArgument(executor: (CommandContext<CommandSource>) -> Int) =
            BrigadierCommand
                .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                .executes(executor)

        private fun settingsLogs(
            context: CommandContext<CommandSource>,
            page: Int,
        ): Int =
            sendLogs(context) { whitelistLogsCommandFacade.getSettingsLogs(page) }

        private fun playerLogs(
            context: CommandContext<CommandSource>,
            page: Int,
        ): Int {
            val playerName = context.getArgument("player", String::class.java)

            return sendLogs(context, playerName) { whitelistLogsCommandFacade.getPlayerLogs(playerName, page) }
        }

        private fun sendLogs(
            context: CommandContext<CommandSource>,
            vararg notFoundArgs: String,
            logs: () -> WhitelistLogsCommandFacade.LogsPage?,
        ): Int =
            try {
                val page = logs()
                if (page == null) {
                    send(context, LanguageKeys.PLAYER_NOT_FOUND, *notFoundArgs)
                    1
                } else {
                    context.source.sendMessage(whitelistLogsCommandFacade.produceMessage(page))
                    0
                }
            } catch (exception: Exception) {
                logger.error("Failed to show the whitelist logs.", exception)
                send(context, LanguageKeys.ACTION_FAILED)
                1
            }

        private fun toggle(
            context: CommandContext<CommandSource>,
            enabled: Boolean,
        ): Int =
            handle(context) {
                val result = whitelistCommandFacade.setWhitelistEnabled(enabled, actorPlayerId(context))
                val (success, unchanged) =
                    if (enabled) {
                        LanguageKeys.WHITELIST_ENABLED to LanguageKeys.WHITELIST_ALREADY_ENABLED
                    } else {
                        LanguageKeys.WHITELIST_DISABLED to LanguageKeys.WHITELIST_ALREADY_DISABLED
                    }

                result to (success to unchanged)
            }

        private fun add(context: CommandContext<CommandSource>): Int =
            playerAction(
                context,
                success = LanguageKeys.PLAYER_ADDED,
                unchanged = LanguageKeys.PLAYER_ALREADY_WHITELISTED,
                action = whitelistCommandFacade::addPlayer,
            )

        private fun remove(context: CommandContext<CommandSource>): Int =
            playerAction(
                context,
                success = LanguageKeys.PLAYER_REMOVED,
                unchanged = LanguageKeys.PLAYER_NOT_WHITELISTED,
                action = whitelistCommandFacade::removePlayer,
            )

        private fun reload(context: CommandContext<CommandSource>): Int =
            try {
                adminCommandsFacade.reload(dataDirectory)
                send(context, LanguageKeys.RELOAD_SUCCESS)
                0
            } catch (exception: Exception) {
                logger.error("Failed to reload the plugin.", exception)
                send(context, LanguageKeys.RELOAD_FAILED)
                1
            }

        private fun playerAction(
            context: CommandContext<CommandSource>,
            success: String,
            unchanged: String,
            action: (playerName: String, actorPlayerId: UUID?) -> WhitelistActionResult,
        ): Int {
            val playerName = context.getArgument("player", String::class.java)

            return handle(context, playerName) {
                action(playerName, actorPlayerId(context)) to (success to unchanged)
            }
        }

        /**
         * Runs [action], sends the message that belongs to its result, and returns the brigadier result.
         * Any failure (e.g. the database being offline) is reported to the sender instead of being thrown at them.
         */
        private fun handle(
            context: CommandContext<CommandSource>,
            vararg messageArgs: String,
            action: () -> Pair<WhitelistActionResult, Pair<String, String>>,
        ): Int =
            try {
                val (result, keys) = action()
                val (successKey, unchangedKey) = keys

                when (result) {
                    WhitelistActionResult.SUCCESS -> send(context, successKey, *messageArgs)
                    WhitelistActionResult.NO_CHANGE -> send(context, unchangedKey, *messageArgs)
                    WhitelistActionResult.PLAYER_NOT_FOUND -> send(context, LanguageKeys.PLAYER_NOT_FOUND, *messageArgs)
                    WhitelistActionResult.ACTOR_NOT_FOUND -> send(context, LanguageKeys.ACTOR_UNKNOWN)
                }

                if (result == WhitelistActionResult.SUCCESS) 0 else 1
            } catch (exception: Exception) {
                logger.error("Failed to execute the whitelist command.", exception)
                send(context, LanguageKeys.ACTION_FAILED)
                1
            }

        private fun actorPlayerId(context: CommandContext<CommandSource>): UUID? = (context.source as? Player)?.uniqueId

        private fun send(
            context: CommandContext<CommandSource>,
            key: String,
            vararg args: String,
        ) {
            val message =
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(key, *args.map { Component.text(it) }.toTypedArray())
                    .build()

            context.source.sendMessage(message)
        }

        private companion object {
            val PERMISSIONS =
                listOf(
                    Permissions.TOGGLE_WHITELIST,
                    Permissions.ADD_PLAYER,
                    Permissions.REMOVE_PLAYER,
                    Permissions.VIEW_LOGS,
                    Permissions.RELOAD_PLUGIN,
                )
        }
    }
