package nl.klrnbk.minecraft.plugins.identity.paper

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext

/**
 * Entry point Paper calls before worlds are loaded. Use this for
 * registering commands via the Brigadier lifecycle event, or anything else
 * that must happen prior to [MCPluginMain.onEnable].
 */
class MCPluginBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.logger.info("MCPlugin bootstrapping...")
    }

    override fun createPlugin(context: PluginProviderContext): MCPluginMain = MCPluginMain()
}
