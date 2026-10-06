package nl.klrnbk.minecraft.plugins.discordId.velocity

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.discordId.velocity.facades.PluginFacade
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "klrnbk-discordId",
    name = "KLRNBK DiscordId",
    version = "1.1.0",
    description =
        "A plugin that allows players to link their Discord account to their Minecraft account.",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    url = "https://klrnbk.nl/github/klrnbk-minecraft-plugins",
    dependencies = [Dependency(id = "klrnbk-runtime-velocity"), Dependency(id = "klrnbk-identity")],
)
class VelocityPlugin
    @Inject
    constructor(
        logger: Logger,
        server: ProxyServer,
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
    }
