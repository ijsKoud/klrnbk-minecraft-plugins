package nl.klrnbk.minecraft.plugins.gui.velocity

import com.google.inject.AbstractModule
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path

class PluginModule(
    private val logger: Logger,
    private val server: ProxyServer,
    private val dataDirectory: Path,
) : AbstractModule() {
    override fun configure() {
        bind(Logger::class.java).toInstance(logger)
        bind(ProxyServer::class.java).toInstance(server)
        bind(Path::class.java).annotatedWith(DataDirectory::class.java).toInstance(dataDirectory)
    }
}
