package net.terraimperia.discord.rewards.services.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

object DiscordLinkTable : UUIDTable("discord_links") {
    val discordId = long("discord_id")
}

data class DiscordLink(
    val uuid: UUID,
    val discordId: Long,
)
