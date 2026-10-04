package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object WhitelistLogEntityTable : UuidTable("whitelist_logs") {
    val identityId = uuid("identity_id")
    val isWhitelisted = bool("is_whitelisted")
    val timestamp = timestamp("last_updated_at")
    val actorIdentityId = uuid("actor_identity_id")

    init {
        // Seek pagination of a player's logs, and the log purge.
        index(false, identityId, timestamp)
        index(false, timestamp)
    }
}

class WhitelistLogEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<WhitelistLogEntity>(WhitelistLogEntityTable)

    var identityId by WhitelistLogEntityTable.identityId
    var isWhitelisted by WhitelistLogEntityTable.isWhitelisted
    var timestamp by WhitelistLogEntityTable.timestamp
    var actorIdentityId by WhitelistLogEntityTable.actorIdentityId

    override fun toString(): String =
        "PlayerWhitelistEntity(id=$id, identityId=$identityId, isWhitelisted=$isWhitelisted, timestamp=$timestamp, actorIdentityId=$actorIdentityId)"
}
