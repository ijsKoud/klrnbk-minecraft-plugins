package nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.KeysetPaginator
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntity
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntityTable
import org.jetbrains.exposed.v1.core.Op
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
        private val paginator =
            KeysetPaginator(
                PlayerConnectionLogEntity,
                PlayerConnectionLogEntityTable,
                PlayerConnectionLogEntityTable.timestamp,
                SortOrder.DESC,
                { it.timestamp },
                Instant::toString,
                Instant::parse,
            )

        fun count(id: Uuid): Long =
            execute {
                PlayerConnectionLogEntity
                    .find { PlayerConnectionLogEntityTable.playerId eq id }
                    .count()
            }

        /**
         * @param pagination The page to read, counted from 0. Newest logs first.
         */
        fun findAllByPlayerId(
            playerId: Uuid,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> = page("player:$playerId", PlayerConnectionLogEntityTable.playerId eq playerId, pagination)

        fun findAllByEventType(
            eventType: ConnectionEventType,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> = page("type:$eventType", PlayerConnectionLogEntityTable.eventType eq eventType, pagination)

        fun findById(id: Uuid): PlayerConnectionLogEntity? =
            execute {
                PlayerConnectionLogEntity.findById(id)
            }

        fun findAllByPlayerIdFilterByEventType(
            playerId: Uuid,
            eventType: ConnectionEventType,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> =
            page(
                "player:$playerId:type:$eventType",
                (PlayerConnectionLogEntityTable.playerId eq playerId) and (PlayerConnectionLogEntityTable.eventType eq eventType),
                pagination,
            )

        private fun page(
            scope: String,
            filter: Op<Boolean>,
            pagination: QueryPagination,
        ): List<PlayerConnectionLogEntity> =
            execute { paginator.findPage(scope, filter, pagination.page + 1, pagination.itemsPerPage) }

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
