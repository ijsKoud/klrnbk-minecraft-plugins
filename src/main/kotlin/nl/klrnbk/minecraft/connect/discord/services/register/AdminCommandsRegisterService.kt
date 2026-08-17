package nl.klrnbk.minecraft.connect.discord.services.register

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.Plugin
import nl.klrnbk.minecraft.connect.discord.commands.AdminReloadCommand
import org.slf4j.Logger

@Singleton
class AdminCommandsRegisterService
    @Inject
    constructor(
        private val adminReloadCommand: AdminReloadCommand,
        private val proxy: ProxyServer,
        private val logger: Logger,
    ) : CommandRegisterService {
        override fun register(plugin: Plugin) {
            val commandManager: CommandManager = proxy.commandManager
            val commandMeta =
                commandManager
                    .metaBuilder("link:discord:reload")
                    .aliases("klrnbk:connect:discord:reload")
                    .plugin(plugin)
                    .build()

            commandManager.register(commandMeta, adminReloadCommand)
            logger.debug("Registered 1 command(s) for category 'ADMIN'")
        }
    }
