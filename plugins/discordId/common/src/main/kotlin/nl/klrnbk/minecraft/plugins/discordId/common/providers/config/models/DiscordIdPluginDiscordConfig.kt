package nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models

import net.dv8tion.jda.api.entities.Activity
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

data class DiscordIdPluginDiscordConfig(
    val boosterRole: String? = null,
    val unlinkCooldown: Long = 30.days.toLong(DurationUnit.MILLISECONDS),
    val statusMessage: String = "Discord & Minecraft players",
    val statusType: Activity.ActivityType = Activity.ActivityType.WATCHING,
    val botToken: String? = null,
)
