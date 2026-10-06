package nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models

data class DiscordIdPluginLogsConfig(
    val enabled: Boolean = true,
    val purgeLogsAfterDays: Int = 90,
)
