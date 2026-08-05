package net.terraimperia.discord.rewards.services.database.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import java.util.UUID

object DiscordLinkRequestCodeTable : UUIDTable("discord_link_request_code") {
    val requestCode = varchar("request_code", 255)
}

data class DiscordLinkRequestCode(
    val uuid: UUID,
    val code: String,
)
