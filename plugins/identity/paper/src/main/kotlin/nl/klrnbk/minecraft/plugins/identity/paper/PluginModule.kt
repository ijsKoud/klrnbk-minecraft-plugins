package nl.klrnbk.minecraft.plugins.identity.paper

import com.google.inject.AbstractModule
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.paper.facades.PaperIdentityApiFacade
import nl.klrnbk.minecraft.plugins.identity.paper.providers.player.PaperPlayerOnlineStatusProvider
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

        bind(IdentityApi::class.java)
            .to(PaperIdentityApiFacade::class.java)
        bind(PlayerOnlineStatusProvider::class.java)
            .toInstance(PaperPlayerOnlineStatusProvider(server))
    }
}
