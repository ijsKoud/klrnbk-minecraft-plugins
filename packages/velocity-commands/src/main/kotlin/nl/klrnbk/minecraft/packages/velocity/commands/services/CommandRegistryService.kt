package nl.klrnbk.minecraft.packages.velocity.commands.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import org.slf4j.Logger

@Singleton
class CommandRegistryService
    @Inject
    constructor(
        private val commands: Set<@JvmSuppressWildcards Command>,
        private val server: ProxyServer,
        private val logger: Logger,
    ) {
        fun register(plugin: Any) {
            commands.forEach {
                val command = it.configure()
                val meta = it.meta(server.commandManager, plugin)

                server.commandManager.register(meta, command)
                logger.info("Registered command: {} with aliases: {}", command.node.name, meta.aliases)
            }
        }
    }
