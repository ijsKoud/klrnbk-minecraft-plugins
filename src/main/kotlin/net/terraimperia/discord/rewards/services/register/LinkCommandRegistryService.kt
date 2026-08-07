package net.terraimperia.discord.rewards.services.register

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.proxy.ProxyServer
import net.terraimperia.discord.rewards.Plugin
import net.terraimperia.discord.rewards.commands.LinkCommand
import org.slf4j.Logger

@Singleton
class LinkCommandRegistryService
    @Inject
    constructor(
        private val linkCommand: LinkCommand,
        private val proxy: ProxyServer,
        private val logger: Logger,
    ) : CommandRegisterService {
        override fun register(plugin: Plugin) {
            val commandManager: CommandManager = proxy.commandManager
            val commandMeta =
                commandManager
                    .metaBuilder("link")
                    .aliases("terra-imperia:link")
                    .plugin(plugin)
                    .build()

            val command = linkCommand.command()
            commandManager.register(commandMeta, command)
            logger.debug("Registered 1 command(s) for category 'LINK'")
        }
    }
