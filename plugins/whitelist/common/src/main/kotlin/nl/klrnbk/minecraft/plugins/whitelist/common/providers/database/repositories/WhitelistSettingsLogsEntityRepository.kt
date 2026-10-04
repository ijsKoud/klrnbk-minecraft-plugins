package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.KeysetPaginator
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistSettingsLogsEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistSettingsLogsEntityTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class WhitelistSettingsLogsEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
) : BaseRepository(context) {
    private val paginator =
        KeysetPaginator(
            WhitelistSettingsLogsEntity,
            WhitelistSettingsLogsEntityTable,
            WhitelistSettingsLogsEntityTable.timestamp,
            SortOrder.DESC,
            { it.timestamp },
            Instant::toString,
            Instant::parse,
        )

    fun count(): Long =
        execute {
            WhitelistSettingsLogsEntity
                .all()
                .count()
        }

    /**
     * @param pagination The page to read, counted from 0. Newest logs first.
     */
    fun findAll(pagination: QueryPagination): List<WhitelistSettingsLogsEntity> =
        execute {
            paginator.findPage(
                scope = "all",
                filter = Op.TRUE,
                page = pagination.page + 1,
                size = pagination.itemsPerPage,
            )
        }

    fun findById(id: Uuid): WhitelistSettingsLogsEntity? =
        execute {
            WhitelistSettingsLogsEntity.findById(id)
        }

    fun create(
        actorIdentityId: Uuid,
        isWhitelistEnabled: Boolean,
    ): WhitelistSettingsLogsEntity =
        execute {
            WhitelistSettingsLogsEntity.new {
                this.timestamp = Clock.System.now()
                this.playerIdentityId = actorIdentityId
                this.isWhitelistEnabled = isWhitelistEnabled
            }
        }

    fun deleteBefore(cutoff: Instant): Int =
        execute {
            WhitelistSettingsLogsEntityTable
                .deleteWhere { WhitelistSettingsLogsEntityTable.timestamp less cutoff }
        }
}
