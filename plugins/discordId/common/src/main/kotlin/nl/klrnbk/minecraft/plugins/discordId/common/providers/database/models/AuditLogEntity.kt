package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

enum class AuditLogAction {
    /** A player linked their Discord account with a link code. */
    LINK,

    /** A player unlinked their own Discord account. */
    UNLINK,

    /** An admin unlinked the Discord account of a player. */
    FORCE_UNLINK,
    RELOAD,
    EXPORT,
    IMPORT,
}

object AuditLogEntityTable : UuidTable("discord_id_audit_logs") {
    val timestamp = timestamp("timestamp")
    val action = enumerationByName<AuditLogAction>("action", 64)

    /** The Identity ID of who did it, null for the console. */
    val actorIdentityId = uuid("actor_identity_id").nullable()

    /** The Identity ID of the player the action was about, if any. */
    val targetIdentityId = uuid("target_identity_id").nullable()
    val discordId = varchar("discord_id", 50).nullable()
    val details = varchar("details", 255).nullable()

    init {
        // Seek pagination of the logs, and the log purge.
        index(false, timestamp)
        index(false, targetIdentityId, timestamp)
    }
}

class AuditLogEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<AuditLogEntity>(AuditLogEntityTable)

    var timestamp by AuditLogEntityTable.timestamp
    var action by AuditLogEntityTable.action
    var actorIdentityId by AuditLogEntityTable.actorIdentityId
    var targetIdentityId by AuditLogEntityTable.targetIdentityId
    var discordId by AuditLogEntityTable.discordId
    var details by AuditLogEntityTable.details

    override fun toString(): String =
        "AuditLogEntity(id=$id, timestamp=$timestamp, action=$action, actorIdentityId=$actorIdentityId, " +
            "targetIdentityId=$targetIdentityId, discordId=$discordId, details=$details)"
}
