package nl.klrnbk.minecraft.connect.discord.services.database.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import java.util.UUID
import kotlin.time.Instant

object PlayerRegistryDetailsTable : UUIDTable("player_registry_details") {
    val lastRegistryDate = timestamp("last_registry_date")
    val booster = bool("is_booster").default(false)
    val registered = bool("is_registered").default(true)
}

data class PlayerRegistryDetails(
    val uuid: UUID,
    val lastRegistryDate: Instant,
    val isBooster: Boolean,
    val isRegistered: Boolean,
)
