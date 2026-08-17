package nl.klrnbk.minecraft.connect.discord.services.register

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.Plugin
import nl.klrnbk.minecraft.connect.discord.commands.UnlinkCommand
import org.slf4j.Logger

@Singleton
class UnlinkCommandRegistryService
    @Inject
    constructor(
        private val unlinkCommand: UnlinkCommand,
        private val proxy: ProxyServer,
        private val logger: Logger,
    ) : CommandRegisterService {
        override fun register(plugin: Plugin) {
            val commandManager: CommandManager = proxy.commandManager
            val commandMeta =
                commandManager
                    .metaBuilder("unlink")
                    .aliases("klrnbk:connect:discord:unlink")
                    .plugin(plugin)
                    .build()

            val command = unlinkCommand.command()
            commandManager.register(commandMeta, command)
            logger.debug("Registered 1 command(s) for category 'UNLINK'")
        }
    }
