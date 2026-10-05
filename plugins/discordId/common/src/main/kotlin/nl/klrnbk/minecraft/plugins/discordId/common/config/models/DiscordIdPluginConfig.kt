package nl.klrnbk.minecraft.plugins.discordId.common.config.models

import nl.klrnbk.minecraft.packages.database.DatasourceConfig

data class DiscordIdPluginConfig(
    val useProxy: Boolean = false,
    val database: DatasourceConfig = DatasourceConfig(),
    val discord: DiscordIdPluginDiscordConfig = DiscordIdPluginDiscordConfig(),
)
