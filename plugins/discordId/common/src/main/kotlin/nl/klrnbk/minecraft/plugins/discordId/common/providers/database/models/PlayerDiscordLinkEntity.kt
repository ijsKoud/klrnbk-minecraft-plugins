package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object PlayerDiscordLinkTable : UuidTable("player_discord_link") {
    val discordId = varchar("discordId", 50).nullable()
    val discordName = varchar("discordName", 255).nullable()
    val isBooster = bool("is_booster")
    val lastUpdatedAt = timestamp("last_updated_at")

    init {
        index(true, discordId)
    }
}

class PlayerDiscordLinkEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerDiscordLinkEntity>(PlayerDiscordLinkTable)

    var discordId by PlayerDiscordLinkTable.discordId
    var discordName by PlayerDiscordLinkTable.discordName
    var isBooster by PlayerDiscordLinkTable.isBooster
    var lastUpdatedAt by PlayerDiscordLinkTable.lastUpdatedAt

    override fun toString(): String =
        "PlayerDiscordLinkEntity(id=$id, discordId=$discordId, discordName=$discordName, isBooster=$isBooster, lastUpdatedAt=$lastUpdatedAt)"
}
