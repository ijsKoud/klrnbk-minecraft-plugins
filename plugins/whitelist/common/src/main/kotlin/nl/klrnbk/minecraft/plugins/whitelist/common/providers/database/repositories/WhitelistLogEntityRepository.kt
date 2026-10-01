package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistLogEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistLogEntityTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class WhitelistLogEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
) : BaseRepository(context) {
    fun count(id: Uuid): Long =
        execute {
            WhitelistLogEntity
                .find { WhitelistLogEntityTable.identityId eq id }
                .count()
        }

    fun findAllByIdentityId(
        identityId: Uuid,
        pagination: QueryPagination,
    ): List<WhitelistLogEntity> =
        execute {
            WhitelistLogEntity
                .find { WhitelistLogEntityTable.identityId eq identityId }
                .orderBy(WhitelistLogEntityTable.timestamp to SortOrder.DESC)
                .limit(pagination.itemsPerPage)
                .offset(pagination.page.toLong() * pagination.itemsPerPage)
                .toList()
        }

    fun findById(id: Uuid): WhitelistLogEntity? =
        execute {
            WhitelistLogEntity.findById(id)
        }

    fun deleteByPlayerId(identityId: Uuid): Boolean =
        execute {
            WhitelistLogEntityTable.deleteWhere { WhitelistLogEntityTable.identityId eq identityId } > 0
        }

    fun create(
        identityId: Uuid,
        actorIdentityId: Uuid,
        isWhitelisted: Boolean,
    ): WhitelistLogEntity =
        execute {
            WhitelistLogEntity.new {
                this.timestamp = Clock.System.now()
                this.identityId = identityId
                this.actorIdentityId = actorIdentityId
                this.isWhitelisted = isWhitelisted
            }
        }

    fun deleteBefore(cutoff: Instant): Int =
        execute {
            WhitelistLogEntityTable
                .deleteWhere { WhitelistLogEntityTable.timestamp less cutoff }
        }
}
