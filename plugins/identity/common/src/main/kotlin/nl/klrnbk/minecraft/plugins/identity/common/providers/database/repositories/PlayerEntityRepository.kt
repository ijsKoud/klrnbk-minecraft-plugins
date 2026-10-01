package nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntity
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntityTable
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun count(): Long =
            execute {
                PlayerEntity
                    .all()
                    .count()
            }

        fun findById(uuid: Uuid): PlayerEntity? =
            execute {
                PlayerEntity
                    .findById(uuid)
            }

        fun findByPlayerId(playerId: Uuid): PlayerEntity? =
            execute {
                PlayerEntity
                    .find { PlayerEntityTable.playerId eq playerId }
                    .firstOrNull()
            }

        fun findByName(name: String): PlayerEntity? =
            execute {
                PlayerEntity
                    .find { PlayerEntityTable.name eq name }
                    .firstOrNull()
            }

        fun findAll(pagination: QueryPagination): List<PlayerEntity> =
            execute {
                PlayerEntity
                    .all()
                    .limit(pagination.itemsPerPage)
                    .offset((pagination.page * pagination.itemsPerPage).toLong())
                    .toList()
            }

        fun updateNameByPlayerId(
            playerId: Uuid,
            newName: String,
        ): Boolean =
            execute {
                val playerEntity =
                    findByPlayerId(playerId)
                        ?: throw IllegalArgumentException("Player with ID $playerId not found")
                if (playerEntity.name == newName) return@execute false

                PlayerEntity.findByIdAndUpdate(playerEntity.id.value) {
                    it.name = newName
                }

                true
            }

        fun create(
            playerId: Uuid,
            name: String,
        ): PlayerEntity =
            execute {
                val playerEntity = findByPlayerId(playerId)
                if (playerEntity != null) {
                    throw IllegalArgumentException("Player with ID $playerId already exists")
                }

                PlayerEntity.new {
                    this.name = name
                    this.playerId = playerId
                    this.firstJoined = Clock.System.now()
                }
            }
    }
