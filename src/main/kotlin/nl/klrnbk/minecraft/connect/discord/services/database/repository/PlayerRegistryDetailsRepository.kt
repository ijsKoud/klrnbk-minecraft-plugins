package nl.klrnbk.minecraft.connect.discord.services.database.repository

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.database.DatabaseService
import nl.klrnbk.minecraft.connect.discord.services.database.tables.PlayerRegistryDetails
import nl.klrnbk.minecraft.connect.discord.services.database.tables.PlayerRegistryDetailsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

@Singleton
class PlayerRegistryDetailsRepository
    @Inject
    constructor(
        private val database: DatabaseService,
    ) {
        fun findById(uuid: UUID): PlayerRegistryDetails? =
            database.query {
                PlayerRegistryDetailsTable
                    .selectAll()
                    .where { PlayerRegistryDetailsTable.id eq uuid }
                    .singleOrNull()
                    ?.let {
                        PlayerRegistryDetails(
                            uuid = it[PlayerRegistryDetailsTable.id].value,
                            lastRegistryDate = it[PlayerRegistryDetailsTable.lastRegistryDate],
                            isBooster = it[PlayerRegistryDetailsTable.booster],
                            isRegistered = it[PlayerRegistryDetailsTable.registered],
                        )
                    }
            }

        fun create(details: PlayerRegistryDetails) =
            database.query {
                PlayerRegistryDetailsTable
                    .insert {
                        it[PlayerRegistryDetailsTable.id] = details.uuid
                        it[PlayerRegistryDetailsTable.lastRegistryDate] = details.lastRegistryDate
                        it[PlayerRegistryDetailsTable.booster] = details.isBooster
                        it[PlayerRegistryDetailsTable.registered] = details.isRegistered
                    }
            }

        fun update(details: PlayerRegistryDetails) =
            database.query {
                PlayerRegistryDetailsTable
                    .update({ PlayerRegistryDetailsTable.id eq details.uuid }) {
                        it[PlayerRegistryDetailsTable.lastRegistryDate] = details.lastRegistryDate
                        it[PlayerRegistryDetailsTable.booster] = details.isBooster
                        it[PlayerRegistryDetailsTable.registered] = details.isRegistered
                    }
            }

        fun resetLinkStatus(uuid: UUID) =
            database.query {
                PlayerRegistryDetailsTable
                    .update({ PlayerRegistryDetailsTable.id eq uuid }) {
                        it[PlayerRegistryDetailsTable.registered] = false
                        it[PlayerRegistryDetailsTable.booster] = false
                    }
            }

        fun deleteById(uuid: UUID) =
            database.query {
                PlayerRegistryDetailsTable
                    .deleteWhere { PlayerRegistryDetailsTable.id eq uuid }
            }
    }
