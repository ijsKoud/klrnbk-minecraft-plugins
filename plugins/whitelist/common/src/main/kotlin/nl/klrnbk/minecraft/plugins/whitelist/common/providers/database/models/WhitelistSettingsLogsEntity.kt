package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

object WhitelistSettingsLogsEntityTable : UuidTable("whitelist_settings_logs") {
    val isWhitelistEnabled = bool("is_whitelist_enabled").default(false)
    val timestamp = timestamp("timestamp")
    val playerIdentityId = uuid("player_identity_uuid")
}

class WhitelistSettingsLogsEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<WhitelistSettingsLogsEntity>(WhitelistSettingsLogsEntityTable)

    var isWhitelistEnabled by WhitelistSettingsLogsEntityTable.isWhitelistEnabled
    var timestamp by WhitelistSettingsLogsEntityTable.timestamp
    var playerIdentityId by WhitelistSettingsLogsEntityTable.playerIdentityId

    override fun toString(): String =
        "WhitelistSettingsLogsEntity(id=$id, isWhitelistEnabled=$isWhitelistEnabled, timestamp=$timestamp, playerIdentityId=$playerIdentityId)"
}
