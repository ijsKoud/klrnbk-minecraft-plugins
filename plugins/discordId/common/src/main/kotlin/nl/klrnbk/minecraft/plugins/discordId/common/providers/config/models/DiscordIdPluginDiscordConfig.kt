package nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models

data class DiscordIdPluginDiscordConfig(
    val boosterRole: String? = null,
    val statusMessage: String = "Discord & Minecraft players",
    val statusType: String? = "WATCHING",
)
