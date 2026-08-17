package nl.klrnbk.minecraft.connect.discord.services.database.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import java.util.UUID

object DiscordLinkTable : UUIDTable("discord_links") {
    val discordId = long("discord_id")
    val discordUsername = varchar("discord_username", 255)
    val booster = bool("is_booster").default(false)
}

data class DiscordLink(
    val uuid: UUID,
    val discordUsername: String,
    val isServerBooster: Boolean,
    val discordId: Long,
)
