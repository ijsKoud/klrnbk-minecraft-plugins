package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistSettingsLogsEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistSettingsLogsEntityTable
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
    fun count(): Long =
        execute {
            WhitelistSettingsLogsEntity
                .all()
                .count()
        }

    fun findAll(pagination: QueryPagination): List<WhitelistSettingsLogsEntity> =
        execute {
            WhitelistSettingsLogsEntity
                .all()
                .orderBy(WhitelistSettingsLogsEntityTable.timestamp to SortOrder.DESC)
                .limit(pagination.itemsPerPage)
                .offset(pagination.page.toLong() * pagination.itemsPerPage)
                .toList()
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
