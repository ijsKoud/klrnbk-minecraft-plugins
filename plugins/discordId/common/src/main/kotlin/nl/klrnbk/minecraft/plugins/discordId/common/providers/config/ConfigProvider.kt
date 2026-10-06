package nl.klrnbk.minecraft.plugins.discordId.common.providers.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models.DiscordIdPluginConfig
import java.nio.file.Path

@Singleton
class ConfigProvider
    @Inject
    constructor(
        private val configStore: YamlConfigStore,
    ) {
        private lateinit var _config: DiscordIdPluginConfig
        val config: DiscordIdPluginConfig get() {
            if (!::_config.isInitialized) {
                throw IllegalStateException("Config has not been loaded yet. Call load() first.")
            }

            return _config
        }

        fun load(dataDirectory: Path): DiscordIdPluginConfig {
            val loadedConfig = configStore.loadOrCreate<DiscordIdPluginConfig>(dataDirectory)
            _config = loadedConfig

            return _config
        }

        fun save(
            dataDirectory: Path,
            config: DiscordIdPluginConfig,
        ) {
            configStore.write(dataDirectory.resolve(YamlConfigStore.DEFAULT_FILE_NAME), config)
        }
    }
