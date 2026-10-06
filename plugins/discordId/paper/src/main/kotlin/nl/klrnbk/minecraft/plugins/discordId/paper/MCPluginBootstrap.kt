package nl.klrnbk.minecraft.plugins.discordId.paper

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext

/**
 * Entry point Paper calls before worlds are loaded. Everything of this plugin happens in
 * [MCPluginMain.onEnable], because it needs the Identity plugin to be enabled first.
 */
class MCPluginBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.logger.info("KLRNBK DiscordId bootstrapping...")
    }

    override fun createPlugin(context: PluginProviderContext): MCPluginMain = MCPluginMain()
}
