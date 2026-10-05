package nl.klrnbk.minecraft.plugins.gui.example

import com.google.inject.Inject
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.GuiProvider
import org.slf4j.Logger

/**
 * A minimal consumer of the GUI framework: `/guiexample` opens [ExampleMenu].
 *
 * Note the pattern every KLRNBK consumer follows: declare a dependency on `klrnbk-gui`, and only call
 * [GuiProvider.get] from `ProxyInitializeEvent` or later — never from the constructor.
 */
@Plugin(
    id = "klrnbk-gui-example",
    name = "KLRNBK GUI Example",
    version = "1.0.1",
    description = "Example and manual test menu for the KLRNBK GUI framework.",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    dependencies = [
        Dependency(id = "klrnbk-runtime-velocity"),
        Dependency(id = "klrnbk-gui"),
    ],
)
class ExamplePlugin
    @Inject
    constructor(
        private val server: ProxyServer,
        private val logger: Logger,
    ) {
        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            val menu = ExampleMenu(GuiProvider.get(), server)
            val meta = server.commandManager.metaBuilder("guiexample").plugin(this).build()
            server.commandManager.register(
                meta,
                SimpleCommand { invocation ->
                    val player = invocation.source() as? Player
                    if (player == null) {
                        invocation.source().sendMessage(net.kyori.adventure.text.Component.text("Players only."))
                    } else {
                        menu.open(player)
                    }
                },
            )
            logger.info("KLRNBK GUI example registered /guiexample")
        }
    }
