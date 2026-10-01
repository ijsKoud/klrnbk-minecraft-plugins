package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object PlayerWhitelistEntityTable : UuidTable("player_whitelists") {
    val identityId = uuid("identity_id").uniqueIndex()
    val isWhitelisted = bool("is_whitelisted")
    val lastUpdatedAt = timestamp("last_updated_at")
    val actorIdentityId = uuid("actor_identity_id")
}

class PlayerWhitelistEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerWhitelistEntity>(PlayerWhitelistEntityTable)

    var identityId by PlayerWhitelistEntityTable.identityId
    var isWhitelisted by PlayerWhitelistEntityTable.isWhitelisted
    var lastUpdatedAt by PlayerWhitelistEntityTable.lastUpdatedAt
    var actorIdentityId by PlayerWhitelistEntityTable.actorIdentityId

    override fun toString(): String =
        "PlayerWhitelistEntity(id=$id, identityId=$identityId, isWhitelisted=$isWhitelisted, lastUpdatedAt=$lastUpdatedAt, actorIdentityId=$actorIdentityId)"
}
