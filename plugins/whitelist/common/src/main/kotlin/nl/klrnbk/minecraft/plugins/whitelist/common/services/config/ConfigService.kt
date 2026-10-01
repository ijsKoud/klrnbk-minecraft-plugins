package nl.klrnbk.minecraft.plugins.whitelist.common.services.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models.WhitelistPluginConfig
import java.nio.file.Path

@Singleton
class ConfigService
    @Inject
    constructor(
        private val configProvider: ConfigProvider,
    ) {
        fun load(dataDirectory: Path): WhitelistPluginConfig = configProvider.load(dataDirectory)

        fun getConfig(): WhitelistPluginConfig = configProvider.config
    }
