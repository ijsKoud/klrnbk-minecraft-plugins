package nl.klrnbk.minecraft.plugins.identity.common.providers.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.packages.common.cryptography.secretKeyToString
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.models.IdentityPluginConfig
import java.nio.file.Path

@Singleton
class ConfigProvider
    @Inject
    constructor(
        private val configStore: YamlConfigStore,
    ) {
        private lateinit var _config: IdentityPluginConfig
        val config: IdentityPluginConfig get() {
            if (!::_config.isInitialized) {
                throw IllegalStateException("Config has not been loaded yet. Call load() first.")
            }

            return _config
        }

        fun load(dataDirectory: Path): IdentityPluginConfig {
            val placeholderMap = mapOf("GENERATED_ENCRYPTION_KEY_HERE" to secretKeyToString(CryptographyUtil.getRandomSecretKey()))
            val loadedConfig = configStore.loadOrCreate<IdentityPluginConfig>(dataDirectory, placeholders = placeholderMap)
            _config = loadedConfig

            return _config
        }

        fun save(
            dataDirectory: Path,
            config: IdentityPluginConfig,
        ) {
            configStore.write(dataDirectory.resolve(YamlConfigStore.DEFAULT_FILE_NAME), config)
        }
    }
