package nl.klrnbk.minecraft.plugins.whitelist.velocity

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistApiFacade
import nl.klrnbk.minecraft.plugins.whitelist.velocity.commands.WhitelistCommand
import org.slf4j.Logger
import java.nio.file.Path

class PluginModule(
    private val logger: Logger,
    private val server: ProxyServer,
    private val dataDirectory: Path,
) : AbstractModule() {
    override fun configure() {
        bind(DatabaseContext::class.java).toInstance(DatabaseContext())
        bind(Logger::class.java).toInstance(logger)
        bind(ProxyServer::class.java).toInstance(server)
        bind(Path::class.java)
            .annotatedWith(DataDirectory::class.java)
            .toInstance(dataDirectory)

        bind(WhitelistApi::class.java).to(WhitelistApiFacade::class.java)

        // Commands
        val commands = Multibinder.newSetBinder(binder(), Command::class.java)
        commands.addBinding().to(WhitelistCommand::class.java)
    }
}
