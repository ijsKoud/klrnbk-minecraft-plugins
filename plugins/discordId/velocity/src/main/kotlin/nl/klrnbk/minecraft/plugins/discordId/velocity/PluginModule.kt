package nl.klrnbk.minecraft.plugins.discordId.velocity

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.velocity.commands.models.Command
import nl.klrnbk.minecraft.plugins.discordId.velocity.commands.DiscordIdCommand
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

        // Commands
        val commands =
            Multibinder.newSetBinder(
                binder(),
                Command::class.java,
            )

        commands.addBinding().to(DiscordIdCommand::class.java)
//        commands.addBinding().to(ExportCommand::class.java)
//        commands.addBinding().to(ImportCommand::class.java)
//        commands.addBinding().to(PlayerlistCommand::class.java)
//        commands.addBinding().to(PlayerInformationCommand::class.java)
//        commands.addBinding().to(PlayerLogsCommand::class.java)
    }
}
