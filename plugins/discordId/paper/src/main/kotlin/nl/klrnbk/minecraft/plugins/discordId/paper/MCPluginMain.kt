package nl.klrnbk.minecraft.plugins.discordId.paper

import com.google.inject.Guice
import com.google.inject.Injector
import nl.klrnbk.minecraft.plugins.discordId.paper.facades.PluginFacade
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

// `open` is required here — MockBukkit's plugin loader creates a ByteBuddy
// proxy subclass of this class when loading it for tests, which fails with
// "Cannot subclass primitive, array or final types" if the class is final
// (Kotlin classes are final by default, unlike Java's). See
// MCPluginMainTest.kt, which exercises this exact path via MockBukkit.load().
open class MCPluginMain : JavaPlugin() {
    private lateinit var injector: Injector

    override fun onEnable() {
        injector = Guice.createInjector(PluginModule(this, LoggerFactory.getLogger(javaClass.name), server))
        injector.getInstance(PluginFacade::class.java).start(this)
    }

    override fun onDisable() {
        if (::injector.isInitialized) {
            injector.getInstance(PluginFacade::class.java).stop()
        }
    }
}
