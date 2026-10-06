package nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models

import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

data class DiscordIdPluginDiscordConfig(
    val boosterRole: String? = null,
    val unlinkCooldown: Long = 30.days.toLong(DurationUnit.MILLISECONDS),
    val statusMessage: String = "Discord & Minecraft players",
    val statusType: String? = "WATCHING",
    val botToken: String? = null,
)
