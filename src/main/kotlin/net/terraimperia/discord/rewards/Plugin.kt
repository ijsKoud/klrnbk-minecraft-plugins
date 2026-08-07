package net.terraimperia.discord.rewards

import com.google.inject.Guice
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.terraimperia.discord.rewards.facade.PluginFacade
import net.terraimperia.discord.rewards.services.register.AdminCommandsRegisterService
import net.terraimperia.discord.rewards.services.register.LinkCommandRegistryService
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "terraimperia__discord-rewards",
    name = "Terra Imperia / Discord / Rewards",
    version = "1.0-SNAPSHOT",
    description = "Plugin that automatically gives in-game perks/ranks for players that received them on Discord",
    url = "terra-imperia.net",
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
