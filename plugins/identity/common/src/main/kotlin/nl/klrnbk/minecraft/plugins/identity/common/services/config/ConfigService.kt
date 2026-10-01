package nl.klrnbk.minecraft.plugins.identity.common.services.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.models.IdentityPluginConfig
import java.nio.file.Path

@Singleton
class ConfigService
    @Inject
    constructor(
        private val configProvider: ConfigProvider,
    ) {
        fun load(dataDirectory: Path): IdentityPluginConfig = configProvider.load(dataDirectory)

        fun getConfig(): IdentityPluginConfig = configProvider.config
    }
