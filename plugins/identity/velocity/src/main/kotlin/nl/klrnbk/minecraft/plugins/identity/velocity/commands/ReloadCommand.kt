package nl.klrnbk.minecraft.plugins.identity.velocity.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.plugin.annotation.DataDirectory
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys.RELOAD_SUCCESS
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import java.nio.file.Path

@Singleton
class ReloadCommand
    @Inject
    constructor(
        private val adminCommandsFacade: AdminCommandsFacade,
        @DataDirectory private val dataDirectory: Path,
    ) : Command {
        override fun configure(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("identityreload")
                    .requires { source -> source.hasPermission(Permissions.RELOAD_PLUGIN) }
                    .executes(::execute)
                    .build()

            return BrigadierCommand(commandNode)
        }

        override fun meta(
            manager: CommandManager,
            plugin: Any,
        ): CommandMeta =
            manager
                .metaBuilder(configure())
                .aliases("identity-reload")
                .plugin(plugin)
                .build()

        private fun execute(source: CommandContext<CommandSource>): Int {
            adminCommandsFacade.reload(dataDirectory)
            val message =
                MessageFactory
                    .factory()
                    .appendAndParseWithMiniMessage(RELOAD_SUCCESS)
                    .build()

            source.source.sendMessage(message)

            return 0
        }
    }
