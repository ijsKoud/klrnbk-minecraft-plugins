package nl.klrnbk.minecraft.connect.discord.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.facade.LinkFacade

@Singleton
class UnlinkCommand
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
//        private val server: ProxyServer,
    ) {
        fun command(): BrigadierCommand {
            val commandNode =
                BrigadierCommand
                    .literalArgumentBuilder("unlink")
                    .requires { source -> source.hasPermission("klrnbk.connect.discord.unlink.use") && linkFacade.hasPermission(source) }
                    .executes(linkFacade::unlink)
//                    .then(forceCommand())
                    .build()

            return BrigadierCommand(commandNode)
        }

//        private fun forceCommand(): LiteralCommandNode<CommandSource> =
//            BrigadierCommand
//                .literalArgumentBuilder("force")
//                .requires { source ->
//                    source.hasPermission("klrnbk.connect.discord.admin.*") ||
//                        source.hasPermission("klrnbk.connect.discord.admin.unlink")
//                }.then(
//                    BrigadierCommand
//                        .requiredArgumentBuilder("player", StringArgumentType.string())
//                        .suggests { _, builder ->
//                            server.allPlayers.forEach {
//                                builder.suggest(it.username)
//                            }
//                            builder.buildFuture()
//                        }.executes(linkFacade::unlink),
//                ).build()
    }
