package nl.klrnbk.minecraft.plugins.identity.common.providers.config.models

data class IdentityPluginLogsConfig(
    val enabled: Boolean = true,
    val logIps: Boolean = true,
    val purgeLogsAfterDays: Int = 30,
)
