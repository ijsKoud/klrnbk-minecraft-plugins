package nl.klrnbk.minecraft.connect.discord.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.StringArgumentType
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.facade.LookupFacade

@Singleton
class LookupCommand
    @Inject
    constructor(
        private val lookupFacade: LookupFacade,
        private val server: ProxyServer,
    ) {
        fun command(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("lookup")
                    .requires { source -> source.hasPermission("klrnbk.connect.discord.lookup.use") }
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("player", StringArgumentType.string())
                            .suggests { _, builder ->
                                server.allPlayers.forEach {
                                    builder.suggest(it.username)
                                }
                                builder.buildFuture()
                            }.executes(lookupFacade::lookupPlayersDiscord),
                    ).build()

            return BrigadierCommand(commandNode)
        }
    }
