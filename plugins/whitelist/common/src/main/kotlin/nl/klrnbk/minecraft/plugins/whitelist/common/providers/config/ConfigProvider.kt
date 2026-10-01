package nl.klrnbk.minecraft.plugins.whitelist.common.providers.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models.WhitelistPluginConfig
import java.nio.file.Path

@Singleton
class ConfigProvider
    @Inject
    constructor(
        private val configStore: YamlConfigStore,
    ) {
        private lateinit var _config: WhitelistPluginConfig
        val config: WhitelistPluginConfig get() {
            if (!::_config.isInitialized) {
                throw IllegalStateException("Config has not been loaded yet. Call load() first.")
            }

            return _config
        }

        fun load(dataDirectory: Path): WhitelistPluginConfig {
            val loadedConfig = configStore.loadOrCreate<WhitelistPluginConfig>(dataDirectory)
            _config = loadedConfig

            return _config
        }

        fun save(
            dataDirectory: Path,
            config: WhitelistPluginConfig,
        ) {
            configStore.write(dataDirectory.resolve(YamlConfigStore.DEFAULT_FILE_NAME), config)
        }
    }
