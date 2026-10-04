package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.KeysetPaginator
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.PlayerWhitelistEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.PlayerWhitelistEntityTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class PlayerWhitelistRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        private val paginator =
            KeysetPaginator(
                PlayerWhitelistEntity,
                PlayerWhitelistEntityTable,
                PlayerWhitelistEntityTable.lastUpdatedAt,
                SortOrder.DESC,
                { it.lastUpdatedAt },
                Instant::toString,
                Instant::parse,
            )

        fun findByPlayerIdentityId(playerIdentityId: Uuid): PlayerWhitelistEntity? =
            execute {
                PlayerWhitelistEntity.find { PlayerWhitelistEntityTable.identityId eq playerIdentityId }.firstOrNull()
            }

        /**
         * @param pagination The page to read, counted from 0. Most recently added first.
         */
        fun findAllWhitelisted(pagination: QueryPagination): List<PlayerWhitelistEntity> =
            execute {
                paginator.findPage(
                    scope = "whitelisted",
                    filter = PlayerWhitelistEntityTable.isWhitelisted eq true,
                    page = pagination.page + 1,
                    size = pagination.itemsPerPage,
                )
            }

        fun countWhitelisted(): Long =
            execute {
                PlayerWhitelistEntity.find { PlayerWhitelistEntityTable.isWhitelisted eq true }.count()
            }

        fun upsert(
            identityId: Uuid,
            actorIdentityId: Uuid,
            isWhitelisted: Boolean,
        ) = execute {
            PlayerWhitelistEntityTable.upsert(PlayerWhitelistEntityTable.identityId) {
                it[PlayerWhitelistEntityTable.identityId] = identityId
                it[PlayerWhitelistEntityTable.isWhitelisted] = isWhitelisted
                it[PlayerWhitelistEntityTable.actorIdentityId] = actorIdentityId
                it[PlayerWhitelistEntityTable.lastUpdatedAt] = Clock.System.now()
            }
        }
    }
