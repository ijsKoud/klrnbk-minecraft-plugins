package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object PlayerDiscordLinkCodeTable : UuidTable("player_discord_link_code") {
    val validUntil = timestamp("valid_until")
    val code = varchar("code", 50)

    init {
        index(true, code)
    }
}

class PlayerDiscordLinkCodeEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerDiscordLinkCodeEntity>(PlayerDiscordLinkCodeTable)

    var code by PlayerDiscordLinkCodeTable.code
    var validUntil by PlayerDiscordLinkCodeTable.validUntil

    override fun toString(): String = "PlayerDiscordLinkCodeEntity(id=$id, code=$code, validUntil=$validUntil)"
}
