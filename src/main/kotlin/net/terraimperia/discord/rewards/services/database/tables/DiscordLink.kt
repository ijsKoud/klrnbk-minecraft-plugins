package net.terraimperia.discord.rewards.services.database.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import java.util.UUID

object DiscordLinkTable : UUIDTable("discord_links") {
    val discordId = long("discord_id")
}

data class DiscordLink(
    val uuid: UUID,
    val discordId: Long,
)
