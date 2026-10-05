package nl.klrnbk.minecraft.plugins.identity.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.plugin.annotation.DataDirectory
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import java.nio.file.Path

@Singleton
class ImportCommand
    @Inject
    constructor(
        private val dataTransferService: DataTransferService,
        @DataDirectory private val dataDirectory: Path,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("identityimport")
                    .requires { source -> source.hasPermission(Permissions.IMPORT_DATA) }
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("file", StringArgumentType.string())
                            .suggests { _, builder ->
                                dataTransferService.getExportSuggestions(dataDirectory).forEach(builder::suggest)
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
                .aliases("identity-import")
                .plugin(plugin)
                .build()

        private fun execute(source: CommandContext<CommandSource>): Int {
            val fileName = source.getArgument("file", String::class.java)

            val message =
                try {
                    val result = dataTransferService.importData(dataDirectory, fileName)
                    MessageFactory
                        .factory()
                        .appendAndParseWithTranslatable(LanguageKeys.IMPORT_SUCCESS, Component.text(result.totalRows))
                        .build()
                } catch (exception: DatabaseTransferException) {
                    MessageFactory
                        .factory()
                        .appendAndParseWithTranslatable(LanguageKeys.TRANSFER_FAILED, Component.text(exception.message.orEmpty()))
                        .build()
                }

            source.source.sendMessage(message)
            return 0
        }
    }
