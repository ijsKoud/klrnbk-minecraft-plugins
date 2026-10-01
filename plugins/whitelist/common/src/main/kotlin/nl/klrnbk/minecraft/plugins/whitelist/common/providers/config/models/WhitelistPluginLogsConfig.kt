package nl.klrnbk.minecraft.plugins.whitelist.common.providers.config.models

data class WhitelistPluginLogsConfig(
    val enabled: Boolean = true,
    val purgeLogsAfterDays: Int = 90,
)
