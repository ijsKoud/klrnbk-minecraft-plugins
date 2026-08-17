package nl.klrnbk.minecraft.connect.discord

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.facade.PluginFacade
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "klrnbk-connect-discord",
    name = "KLRNBK Connect / Discord",
    version = "1.0-SNAPSHOT",
    description = "Plugin that automatically gives in-game perks/ranks for players that received them on Discord",
    url = "klrnbk.nl/projects/klrnbk-minecraft-plugins",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    dependencies = [
        Dependency(
            id = "nlogin",
            optional = true,
        ),
    ],
)
class Plugin
    @Inject
    constructor(
        logger: Logger,
        proxy: ProxyServer,
        @DataDirectory private val dataDirectory: Path,
    ) {
        private val injector = Guice.createInjector(PluginModule(logger, proxy, dataDirectory))

        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            injector.getInstance(PluginFacade::class.java).start(this)
        }

        @Subscribe
        fun onServerPostConnect(event: ServerPostConnectEvent) {
            injector.getInstance(PluginFacade::class.java).updatePlayerUsernameInDatabase(event.player.uniqueId, event.player.username)
        }
    }
