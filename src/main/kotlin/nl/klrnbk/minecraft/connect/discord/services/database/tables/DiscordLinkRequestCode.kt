package nl.klrnbk.minecraft.connect.discord.services.database.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import java.util.UUID

object DiscordLinkRequestCodeTable : UUIDTable("discord_link_request_code") {
    val requestCode = varchar("request_code", 255)
}

data class DiscordLinkRequestCode(
    val uuid: UUID,
    val code: String,
)
