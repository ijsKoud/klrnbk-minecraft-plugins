package nl.klrnbk.minecraft.plugins.discordId.paper

import com.google.inject.Guice
import nl.klrnbk.minecraft.plugins.discordId.paper.facades.PluginFacade
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

// `open` is required here — MockBukkit's plugin loader creates a ByteBuddy
// proxy subclass of this class when loading it for tests, which fails with
// "Cannot subclass primitive, array or final types" if the class is final
// (Kotlin classes are final by default, unlike Java's). See
// MCPluginMainTest.kt, which exercises this exact path via MockBukkit.load().
open class MCPluginMain : JavaPlugin() {
    private var facade: PluginFacade? = null

    override fun onEnable() {
        val injector = Guice.createInjector(PluginModule(this, LoggerFactory.getLogger(javaClass.name), server))
        // Resolving the facade is what fails when the Identity plugin is missing; nothing has started by then,
        // so onDisable (which Paper still calls after a failed enable) has nothing to stop.
        facade = injector.getInstance(PluginFacade::class.java)
        facade?.start(this)
    }

    override fun onDisable() {
        facade?.stop()
    }
}
