package nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models

import nl.klrnbk.minecraft.packages.database.DatasourceConfig

data class WhitelistPluginConfig(
    val useProxy: Boolean = false,
    val kickMessage: String = "You are not whitelisted on this server. Please contact the server administrator for access.",
    val logs: WhitelistPluginLogsConfig = WhitelistPluginLogsConfig(),
    val database: DatasourceConfig = DatasourceConfig(),
)
