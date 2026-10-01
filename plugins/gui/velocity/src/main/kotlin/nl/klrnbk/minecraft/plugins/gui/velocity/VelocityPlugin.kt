package nl.klrnbk.minecraft.plugins.gui.velocity

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.velocity.facades.PluginFacade
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "klrnbk-gui",
    name = "KLRNBK GUI",
    version = "1.0.0",
    description = "Inventory GUI framework for KLRNBK Velocity plugins (Java and Bedrock/Geyser).",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    url = "https://klrnbk.nl/github/klrnbk-minecraft-plugins",
    dependencies = [
        Dependency(id = "klrnbk-runtime-velocity"),
        // The protocol layer. Required: without it nothing can be sent to clients.
        Dependency(id = "packetevents"),
        // Only used (through reflection) to recognise Bedrock players.
        Dependency(id = "floodgate", optional = true),
        Dependency(id = "geyser", optional = true),
        // Optional placeholder sources for YAML menus (see the README).
        Dependency(id = "miniplaceholders", optional = true),
        Dependency(id = "papiproxybridge", optional = true),
    ],
)
class VelocityPlugin
    @Inject
    constructor(
        logger: Logger,
        server: ProxyServer,
        @DataDirectory dataDirectory: Path,
    ) {
        private val injector = Guice.createInjector(PluginModule(logger, server, dataDirectory))
        private val facade: PluginFacade get() = injector.getInstance(PluginFacade::class.java)

        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            facade.start(this)
        }

        @Subscribe
        fun onProxyShutdown(event: ProxyShutdownEvent) {
            facade.stop()
        }

        @Subscribe
        fun onDisconnect(event: DisconnectEvent) {
            facade.onDisconnect(event.player)
        }

        @Subscribe
        fun onServerConnected(event: ServerConnectedEvent) {
            // The first connection after login has no previous server and nothing to clean up.
            if (event.previousServer.isPresent) facade.onServerSwitch(event.player)
        }

        @Subscribe
        fun onServerPostConnect(event: ServerPostConnectEvent) {
            facade.onServerPostConnect(event.player)
        }
    }
