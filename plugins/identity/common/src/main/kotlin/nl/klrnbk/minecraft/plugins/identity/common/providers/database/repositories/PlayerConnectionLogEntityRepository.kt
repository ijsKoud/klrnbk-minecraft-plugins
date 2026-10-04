package nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntity
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntityTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class PlayerConnectionLogEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun count(id: Uuid): Long =
            execute {
                PlayerConnectionLogEntity
                    .find { PlayerConnectionLogEntityTable.playerId eq id }
                    .count()
            }

        fun findAllByPlayerId(
            playerId: Uuid,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> =
            execute {
                PlayerConnectionLogEntity
                    .find { PlayerConnectionLogEntityTable.playerId eq playerId }
                    .orderBy(PlayerConnectionLogEntityTable.timestamp to SortOrder.DESC)
                    .limit(pagination.itemsPerPage)
                    .offset(pagination.page.toLong() * pagination.itemsPerPage)
                    .toList()
            }

        fun findAllByEventType(
            eventType: ConnectionEventType,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> =
            execute {
                PlayerConnectionLogEntity
                    .find { PlayerConnectionLogEntityTable.eventType eq eventType }
                    .limit(pagination.itemsPerPage)
                    .offset((pagination.page * pagination.itemsPerPage).toLong())
                    .toList()
            }

        fun findById(id: Uuid): PlayerConnectionLogEntity? =
            execute {
                PlayerConnectionLogEntity.findById(id)
            }

        fun findAllByPlayerIdFilterByEventType(
            playerId: Uuid,
            eventType: ConnectionEventType,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> =
            execute {
                PlayerConnectionLogEntity
                    .find {
                        (PlayerConnectionLogEntityTable.playerId eq playerId) and
                            (PlayerConnectionLogEntityTable.eventType eq eventType)
                    }.limit(pagination.itemsPerPage)
                    .offset((pagination.page * pagination.itemsPerPage).toLong())
                    .toList()
            }

        fun deleteByPlayerId(playerId: Uuid): Boolean =
            execute {
                PlayerConnectionLogEntityTable.deleteWhere { PlayerConnectionLogEntityTable.playerId eq playerId } > 0
            }

        fun create(
            playerId: Uuid,
            eventType: ConnectionEventType,
            serverName: String,
            serverIp: String,
            playerIp: String?,
        ): PlayerConnectionLogEntity =
            execute {
                PlayerConnectionLogEntity.new {
                    this.playerId = playerId
                    this.timestamp = Clock.System.now()
                    this.eventType = eventType
                    this.serverName = serverName
                    this.serverIp = serverIp
                    this.playerIp = playerIp
                }
            }

        fun deleteBefore(cutoff: Instant): Int =
            execute {
                PlayerConnectionLogEntityTable
                    .deleteWhere { PlayerConnectionLogEntityTable.timestamp less cutoff }
            }
    }
