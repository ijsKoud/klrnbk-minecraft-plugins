package net.terraimperia.discord.rewards.services.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.plugin.annotation.DataDirectory
import net.terraimperia.discord.rewards.services.config.models.PluginConfigModel
import net.terraimperia.discord.rewards.utils.transformDurationToText
import org.slf4j.Logger
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.HeaderMode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.measureTime

@Singleton
class ConfigService
    @Inject
    constructor(
        @DataDirectory private val dataDirectory: Path,
        private val logger: Logger,
    ) {
        private var _config: PluginConfigModel? = null
        val config: PluginConfigModel get() =
            _config
                ?: throw IllegalArgumentException("Config is not loaded yet. Call load() first before accessing it.")

        @Throws(IOException::class)
        fun load(): PluginConfigModel {
            logger.info("Loading config...")
            val duration = measureTime(::loadConfiguration)
            logger.info("Config loaded in ${transformDurationToText(duration)} (config version: ${_config?.version})")

            return _config!!
        }

        @Throws(IOException::class)
        fun reload(): PluginConfigModel = load()

        @Throws(IOException::class)
        private fun loadConfiguration(): PluginConfigModel {
            val configFile: Path = dataDirectory.resolve("config.conf")
            val loader: HoconConfigurationLoader =
                HoconConfigurationLoader
                    .builder()
                    .path(configFile)
                    .indent(2)
                    .headerMode(HeaderMode.PRESERVE)
                    .build()

            Files.createDirectories(configFile.parent)

            if (Files.notExists(configFile)) {
                val node: ConfigurationNode = loader.createNode()

                _config = PluginConfigModel()
                node.set(PluginConfigModel::class.java, _config)

                loader.save(node)
                return _config!!
            }

            val node: ConfigurationNode = loader.load()
            _config = node.get(PluginConfigModel::class.java, _config) ?: PluginConfigModel()
            return _config!!
        }
    }
