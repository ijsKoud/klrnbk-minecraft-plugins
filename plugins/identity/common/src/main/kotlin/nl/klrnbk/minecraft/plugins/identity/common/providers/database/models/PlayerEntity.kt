package nl.klrnbk.minecraft.plugins.identity.common.providers.database.models

import nl.klrnbk.minecraft.packages.common.constants.MINECRAFT_USERNAME_MAX_LENGTH
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object PlayerEntityTable : UuidTable("players") {
    val name = varchar("player_name", MINECRAFT_USERNAME_MAX_LENGTH)
    val playerId = uuid("player_id")
    val firstJoined = timestamp("first_joined")

    init {
        // Looked up by Minecraft UUID on every login, and by name.
        index(false, playerId)
        index(false, name)
    }
}

class PlayerEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerEntity>(PlayerEntityTable)

    var name by PlayerEntityTable.name
    var playerId by PlayerEntityTable.playerId
    var firstJoined by PlayerEntityTable.firstJoined

    override fun toString(): String = "PlayerEntity(id=$id, name=$name, playerId=$playerId, firstJoined=$firstJoined)"
}
