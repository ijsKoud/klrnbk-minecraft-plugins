package nl.klrnbk.minecraft.plugins.identity.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.common.config.PluginConfig
import nl.klrnbk.minecraft.plugins.common.logging.PluginLogger
import nl.klrnbk.minecraft.plugins.identity.common.ExamplePluginInfo
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "mcplugin",
    name = "MCPlugin",
    version = "1.0.1",
    description = "Example Velocity proxy plugin from the Kotlin monorepo.",
    authors = ["YourName"],
)
class MCVelocityPlugin
    @Inject
    constructor(
        private val server: ProxyServer,
        private val logger: Logger,
        @DataDirectory private val dataDirectory: Path,
    ) {
        private val pluginLogger = PluginLogger { message -> logger.info(message) }
        private var config: PluginConfig = PluginConfig()

        @Subscribe
        fun onProxyInitialize(event: ProxyInitializeEvent) {
            pluginLogger.info("${ExamplePluginInfo.PLUGIN_ID} (Velocity) initialized (debug=${config.debug}).")
            // Register the plugin-messaging channel shared with the Paper side,
            // e.g. server.channelRegistrar.register(...) using
            // nl.klrnbk.minecraft.plugins.common.messaging.Channels.PLAYER_SYNC
        }
    }
