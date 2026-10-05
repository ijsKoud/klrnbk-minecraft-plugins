package nl.klrnbk.minecraft.plugins.identity.velocity

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.velocity.facades.PluginFacade
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlin.uuid.toKotlinUuid

@Plugin(
    id = "klrnbk-identity",
    name = "KLRNBK Identity",
    version = "1.0.1",
    description =
        "Keeps track of all players that have ever joined the server. This plugin is a helper plugin for all other KLRNBK plugins that require player data.",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    url = "https://klrnbk.nl/github/klrnbk-minecraft-plugins",
    dependencies = [Dependency(id = "klrnbk-runtime-velocity")],
)
class VelocityPlugin
    @Inject
    constructor(
        logger: Logger,
        val server: ProxyServer,
        @DataDirectory val dataDirectory: Path,
    ) {
        private val injector = Guice.createInjector(PluginModule(logger, server, dataDirectory))

        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            injector
                .getInstance(PluginFacade::class.java)
                .start(this)

            val api = injector.getInstance(IdentityApi::class.java)
            IdentityProvider.register(api)
        }

        @Subscribe
        fun onProxyShutdownEvent(event: ProxyShutdownEvent) {
            injector
                .getInstance(PluginFacade::class.java)
                .stop()
        }

        private fun getPlayerIp(connection: InboundConnection): String =
            "${connection.remoteAddress.address.hostAddress}:${connection.remoteAddress.port}"

        @Subscribe(priority = Short.MAX_VALUE)
        fun onLoginEvent(event: LoginEvent) {
            injector
                .getInstance(PluginFacade::class.java)
                .registerAndOrLogPlayerConnection(
                    playerId = event.player.uniqueId.toKotlinUuid(),
                    playerName = event.player.username,
                    serverIp =
                        event.player.virtualHost
                            .getOrNull()
                            ?.hostString ?: "unknown",
                    playerIp = getPlayerIp(event.player),
                )
        }

        @Subscribe
        fun onDisconnectEvent(event: DisconnectEvent) {
            injector
                .getInstance(PluginFacade::class.java)
                .logPlayerDisconnection(
                    playerId = event.player.uniqueId.toKotlinUuid(),
                    serverIp =
                        event.player.virtualHost
                            .getOrNull()
                            ?.hostString ?: "unknown",
                    playerIp = getPlayerIp(event.player),
                )
        }
    }
