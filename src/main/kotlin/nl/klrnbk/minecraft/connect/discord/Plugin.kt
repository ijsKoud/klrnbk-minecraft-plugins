package nl.klrnbk.minecraft.connect.discord

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.connect.discord.facade.PluginFacade
import nl.klrnbk.minecraft.connect.discord.services.register.AdminCommandsRegisterService
import nl.klrnbk.minecraft.connect.discord.services.register.LinkCommandRegistryService
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "klrnbk-connect-discord",
    name = "KLRNBK Connect / Discord",
    version = "1.0-SNAPSHOT",
    description = "Plugin that automatically gives in-game perks/ranks for players that received them on Discord",
    url = "klrnbk.nl/projects/klrnbk-minecraft-plugins",
    authors = ["ijsKoud <daan@klrnbk.nl>"],
)
class Plugin
    @Inject
    constructor(
        private val logger: Logger,
        private val proxy: ProxyServer,
        @DataDirectory private val dataDirectory: Path,
    ) {
        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            val injector = Guice.createInjector(PluginModule(logger, proxy, dataDirectory))
            injector.getInstance(PluginFacade::class.java).start(this)
        }
    }
