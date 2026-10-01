package nl.klrnbk.minecraft.plugins.identity.common.providers.database.models

import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object PlayerConnectionLogEntityTable : UuidTable("player_connection_logs") {
    val playerId = uuid("player_id")
    val timestamp = timestamp("timestamp")
    val eventType = enumerationByName<ConnectionEventType>("event_type", 255)
    val serverName = varchar("server_name", 255)
    val serverIp = varchar("server_ip", 255)
    val playerIp = varchar("player_ip", 255).nullable()
}

class PlayerConnectionLogEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerConnectionLogEntity>(PlayerConnectionLogEntityTable)

    var playerId by PlayerConnectionLogEntityTable.playerId
    var timestamp by PlayerConnectionLogEntityTable.timestamp
    var eventType by PlayerConnectionLogEntityTable.eventType
    var serverName by PlayerConnectionLogEntityTable.serverName
    var serverIp by PlayerConnectionLogEntityTable.serverIp
    var playerIp by PlayerConnectionLogEntityTable.playerIp

    override fun toString(): String =
        "PlayerConnectionLogEntity(id=$id, playerId=$playerId, timestamp=$timestamp, eventType=$eventType, serverName=$serverName, serverIp=$serverIp)"
}
