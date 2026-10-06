package nl.klrnbk.minecraft.plugins.discordId.paper

import com.google.inject.AbstractModule
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger

class PluginModule(
    private val plugin: JavaPlugin,
    private val logger: Logger,
    private val server: Server,
) : AbstractModule() {
    override fun configure() {
        bind(DatabaseContext::class.java).toInstance(DatabaseContext())
        bind(JavaPlugin::class.java).toInstance(plugin)
        bind(Logger::class.java).toInstance(logger)
        bind(Server::class.java).toInstance(server)

        // IdentityApi is deliberately not bound: the Identity plugin registers the instance in IdentityProvider.
    }
}
