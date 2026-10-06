package nl.klrnbk.minecraft.plugins.discordId.common.services.config

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models.DiscordIdPluginConfig
import java.nio.file.Path

@Singleton
class ConfigService
    @Inject
    constructor(
        private val configProvider: ConfigProvider,
    ) {
        fun load(dataDirectory: Path): DiscordIdPluginConfig = configProvider.load(dataDirectory)

        fun getConfig(): DiscordIdPluginConfig = configProvider.config
    }
