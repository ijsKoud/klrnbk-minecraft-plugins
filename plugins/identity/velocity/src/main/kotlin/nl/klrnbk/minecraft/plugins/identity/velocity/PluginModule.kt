package nl.klrnbk.minecraft.plugins.identity.velocity

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.velocity.commands.PlayerInformationCommand
import nl.klrnbk.minecraft.plugins.identity.velocity.commands.PlayerLogsCommand
import nl.klrnbk.minecraft.plugins.identity.velocity.commands.PlayerlistCommand
import nl.klrnbk.minecraft.plugins.identity.velocity.commands.ReloadCommand
import nl.klrnbk.minecraft.plugins.identity.velocity.facades.VelocityIdentityApiFacade
import nl.klrnbk.minecraft.plugins.identity.velocity.providers.player.VelocityPlayerOnlineStatusProvider
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

        bind(IdentityApi::class.java)
            .to(VelocityIdentityApiFacade::class.java)
        bind(PlayerOnlineStatusProvider::class.java)
            .toInstance(VelocityPlayerOnlineStatusProvider(server))

        // Commands
        val commands =
            Multibinder.newSetBinder(
                binder(),
                Command::class.java,
            )

        commands.addBinding().to(ReloadCommand::class.java)
        commands.addBinding().to(PlayerlistCommand::class.java)
        commands.addBinding().to(PlayerInformationCommand::class.java)
        commands.addBinding().to(PlayerLogsCommand::class.java)
    }
}
