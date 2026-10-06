package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.KeysetPaginator
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogAction
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogEntityTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class AuditLogEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        private val paginator =
            KeysetPaginator(
                AuditLogEntity,
                AuditLogEntityTable,
                AuditLogEntityTable.timestamp,
                SortOrder.DESC,
                { it.timestamp },
                Instant::toString,
                Instant::parse,
            )

        fun count(): Long = execute { AuditLogEntity.all().count() }

        /**
         * @param pagination The page to read, counted from 0. Newest logs first.
         */
        fun findAll(pagination: QueryPagination): List<AuditLogEntity> =
            execute {
                paginator.findPage(
                    scope = "all",
                    filter = Op.TRUE,
                    page = pagination.page + 1,
                    size = pagination.itemsPerPage,
                )
            }

        fun create(
            action: AuditLogAction,
            actorIdentityId: Uuid?,
            targetIdentityId: Uuid?,
            discordId: String?,
            details: String?,
        ): AuditLogEntity =
            execute {
                AuditLogEntity.new {
                    this.timestamp = Clock.System.now()
                    this.action = action
                    this.actorIdentityId = actorIdentityId
                    this.targetIdentityId = targetIdentityId
                    this.discordId = discordId
                    this.details = details
                }
            }

        fun deleteBefore(cutoff: Instant): Int =
            execute {
                AuditLogEntityTable.deleteWhere { AuditLogEntityTable.timestamp less cutoff }
            }
    }
