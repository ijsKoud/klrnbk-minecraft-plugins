package nl.klrnbk.minecraft.connect.discord.services.register

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.Plugin
import nl.klrnbk.minecraft.connect.discord.commands.LinkCommand
import nl.klrnbk.minecraft.connect.discord.commands.LookupCommand
import org.slf4j.Logger

@Singleton
class LookupCommandRegisterService
    @Inject
    constructor(
        private val lookupCommand: LookupCommand,
        private val proxy: ProxyServer,
        private val logger: Logger,
    ) : CommandRegisterService {
        override fun register(plugin: Plugin) {
            val commandManager: CommandManager = proxy.commandManager
            val commandMeta =
                commandManager
                    .metaBuilder("lookup")
                    .aliases("klrnbk:connect:discord:lookup")
                    .plugin(plugin)
                    .build()

            val command = lookupCommand.command()
            commandManager.register(commandMeta, command)
            logger.debug("Registered 1 command(s) for category 'LOOKUP'")
        }
    }
