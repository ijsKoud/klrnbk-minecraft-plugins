package nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models

import nl.klrnbk.minecraft.packages.database.DatasourceConfig

data class DiscordIdPluginConfig(
    val useProxy: Boolean = false,
    val logs: DiscordIdPluginLogsConfig = DiscordIdPluginLogsConfig(),
    val database: DatasourceConfig = DatasourceConfig(),
    val discord: DiscordIdPluginDiscordConfig = DiscordIdPluginDiscordConfig(),
)
