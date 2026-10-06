package nl.klrnbk.minecraft.plugins.discordId.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.Permissions
import nl.klrnbk.minecraft.plugins.discordId.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import java.nio.file.Path
import kotlin.uuid.toKotlinUuid

@Singleton
class DiscordIdCommand
    @Inject
    constructor(
        private val adminCommandsFacade: AdminCommandsFacade,
        private val linkFacade: LinkFacade,
        @DataDirectory private val dataDirectory: Path,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val identityApi = IdentityProvider.get()

            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("discordId")
                    .then(
                        BrigadierCommand
                            .literalArgumentBuilder("reload")
                            .requires { source -> source.hasPermission(Permissions.ADMIN_RELOAD) }
                            .executes(::executeReload)
                            .build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("import")
                            .requires { source -> source.hasPermission(Permissions.ADMIN_IMPORT) }
                            .then(
                                BrigadierCommand
                                    .requiredArgumentBuilder("file", StringArgumentType.string())
                                    .executes(::executeImport)
                                    .build(),
                            ).build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("export")
                            .requires { source -> source.hasPermission(Permissions.ADMIN_EXPORT) }
                            .executes(::executeExport)
                            .build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("link")
                            .requires { source -> source.hasPermission(Permissions.LINK) && source is Player }
                            .executes(::executeLink)
                            .build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("unlink")
                            .requires { source -> source.hasPermission(Permissions.UNLINK) && source is Player }
                            .executes(::executeUnlink)
                            .build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("lookup")
                            .requires { source -> source.hasPermission(Permissions.LOOKUP) }
                            .then(
                                BrigadierCommand
                                    .requiredArgumentBuilder("player", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        identityApi.getPlayerNames(builder.remaining).forEach(builder::suggest)
                                        builder.buildFuture()
                                    }.executes(::executeLookup)
                                    .build(),
                            ).build(),
                    ).then(
                        BrigadierCommand
                            .literalArgumentBuilder("adminunlink")
                            .requires { source -> source.hasPermission(Permissions.UNLINK_FORCED) }
                            .then(
                                BrigadierCommand
                                    .requiredArgumentBuilder("player", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        identityApi.getPlayerNames(builder.remaining).forEach(builder::suggest)
                                        builder.buildFuture()
                                    }.executes(::executeUnlinkForced)
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
                .aliases("klrnbk-discordId", "discordid")
                .plugin(plugin)
                .build()

        // The Minecraft UUID of the sender, null for the console.
        private fun actorOf(context: CommandContext<CommandSource>) = (context.source as? Player)?.uniqueId?.toKotlinUuid()

        private fun executeReload(source: CommandContext<CommandSource>): Int {
            adminCommandsFacade.reload(dataDirectory, actorOf(source))
            val message =
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_RELOAD_SUCCESS)
                    .build()

            source.source.sendMessage(message)

            return 0
        }

        private fun executeImport(source: CommandContext<CommandSource>): Int {
            val fileName = source.getArgument("file", String::class.java)
            val message = adminCommandsFacade.importData(dataDirectory, fileName, actorOf(source))
            source.source.sendMessage(message)

            return 0
        }

        private fun executeExport(source: CommandContext<CommandSource>): Int {
            val message = adminCommandsFacade.exportData(dataDirectory, actorOf(source))
            source.source.sendMessage(message)

            return 0
        }

        private fun executeUnlink(source: CommandContext<CommandSource>): Int {
            val player = source.source as Player
            val isBypassed = player.hasPermission(Permissions.UNLINK_BYPASS)

            val message = linkFacade.unlinkPlayer(player.uniqueId.toKotlinUuid(), false, isBypassed)
            source.source.sendMessage(message)

            return 0
        }

        private fun executeLink(source: CommandContext<CommandSource>): Int {
            val player = source.source as Player
            val message = linkFacade.getLinkCodeForPlayer(player.uniqueId.toKotlinUuid())

            source.source.sendMessage(message)

            return 0
        }

        private fun executeUnlinkForced(source: CommandContext<CommandSource>): Int {
            val player = source.getArgument("player", String::class.java)
            val message = linkFacade.forceUnlinkPlayer(player, actorOf(source))
            source.source.sendMessage(message)

            return 0
        }

        private fun executeLookup(source: CommandContext<CommandSource>): Int {
            val player = source.getArgument("player", String::class.java)
            val message = linkFacade.lookupPlayer(player)
            source.source.sendMessage(message)

            return 0
        }
    }
