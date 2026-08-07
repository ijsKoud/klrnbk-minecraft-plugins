package net.terraimperia.discord.rewards.services.database.tables

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

object PlayerRegistryDetailsTable : UUIDTable("player_registry_details") {
    val lastRegistryDate = datetime("last_registry_date")
    val booster = bool("is_booster").default(false)
    val registered = bool("is_registered").default(true)
}

data class PlayerRegistryDetails(
    val uuid: UUID,
    val lastRegistryDate: LocalDateTime,
    val isBooster: Boolean,
    val isRegistered: Boolean,
)
