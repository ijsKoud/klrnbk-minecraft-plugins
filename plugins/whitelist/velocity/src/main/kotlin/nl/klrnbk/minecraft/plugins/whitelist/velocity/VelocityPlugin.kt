package nl.klrnbk.minecraft.plugins.whitelist.velocity

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistProvider
import nl.klrnbk.minecraft.plugins.whitelist.velocity.facades.PluginFacade
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "klrnbk-whitelist",
    name = "KLRNBK Whitelist",
    version = "1.0.1",
    description = "Enforce a proxy-wide whitelist for Minecraft servers.",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
    url = "https://klrnbk.nl/github/klrnbk-minecraft-plugins",
    dependencies = [
        Dependency(id = "klrnbk-runtime-velocity"),
        Dependency(id = "klrnbk-identity", optional = false),
    ],
)
class VelocityPlugin
    @Inject
    constructor(
        val server: ProxyServer,
        logger: Logger,
        @DataDirectory val dataDirectory: Path,
    ) {
        private val injector = Guice.createInjector(PluginModule(logger, server, dataDirectory))

        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            injector
                .getInstance(PluginFacade::class.java)
                .start(this)

            WhitelistProvider.register(injector.getInstance(WhitelistApi::class.java))
        }

        @Subscribe
        fun onProxyShutdownEvent(event: ProxyShutdownEvent) {
            WhitelistProvider.unregister()
            injector
                .getInstance(PluginFacade::class.java)
                .stop()
        }
    }
