package nl.klrnbk.minecraft.plugins.identity.paper

import nl.klrnbk.minecraft.plugins.common.config.PluginConfig
import nl.klrnbk.minecraft.plugins.common.logging.PluginLogger
import nl.klrnbk.minecraft.plugins.identity.common.ExamplePluginInfo
import org.bukkit.plugin.java.JavaPlugin

// `open` is required here — MockBukkit's plugin loader creates a ByteBuddy
// proxy subclass of this class when loading it for tests, which fails with
// "Cannot subclass primitive, array or final types" if the class is final
// (Kotlin classes are final by default, unlike Java's). See
// MCPluginMainTest.kt, which exercises this exact path via MockBukkit.load().
open class MCPluginMain : JavaPlugin() {
    lateinit var config: PluginConfig
        private set

    private val pluginLogger = PluginLogger { message -> logger.info(message) }

    override fun onEnable() {
        config = PluginConfig() // TODO: load from disk via your config skill/lib of choice
        pluginLogger.info("${ExamplePluginInfo.PLUGIN_ID} enabled (debug=${config.debug}).")

        server.pluginManager.registerEvents(PlayerJoinListener(this), this)
    }

    override fun onDisable() {
        pluginLogger.info("${ExamplePluginInfo.PLUGIN_ID} disabled.")
    }
}
