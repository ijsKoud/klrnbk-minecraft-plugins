package nl.klrnbk.minecraft.plugins.discordId.common.services.audit

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogAction
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.AuditLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.slf4j.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Keeps a record of who linked, unlinked or administrated what.
 */
@Singleton
class AuditLogService
    @Inject
    constructor(
        private val auditLogEntityRepository: AuditLogEntityRepository,
        private val configProvider: ConfigProvider,
        private val logger: Logger,
    ) {
        /**
         * Logs [action]. Does nothing when logging is disabled in the config. A failure to write the log is
         * reported in the console and never fails the action that is being logged.
         *
         * @param actorIdentityId who did it, null for the console.
         * @param targetIdentityId the player the action was about.
         */
        fun log(
            action: AuditLogAction,
            actorIdentityId: Uuid? = null,
            targetIdentityId: Uuid? = null,
            discordId: String? = null,
            details: String? = null,
        ) {
            if (!configProvider.config.logs.enabled) return

            try {
                auditLogEntityRepository.create(action, actorIdentityId, targetIdentityId, discordId, details?.take(MAX_DETAILS_LENGTH))
            } catch (exception: Exception) {
                logger.error("Could not write the audit log entry for $action.", exception)
            }
        }

        /**
         * The Identity ID of the sender of a command: [playerId] is a Minecraft UUID, null is the console.
         * Returns null for the console and for players Identity doesn't know.
         */
        fun actorIdentityId(playerId: Uuid?): Uuid? {
            if (playerId == null) return null
            return IdentityProvider
                .get()
                .getPlayerFromUuid(playerId.toJavaUuid())
                ?.id
                ?.toKotlinUuid()
        }

        fun getLogs(pagination: QueryPagination): List<AuditLogEntity> = auditLogEntityRepository.findAll(pagination)

        fun getLogsCount(): Long = auditLogEntityRepository.count()

        /**
         * Deletes all logs older than [retention].
         *
         * @return the number of deleted logs.
         */
        fun deleteLogsOlderThan(retention: Duration): Int = auditLogEntityRepository.deleteBefore(Clock.System.now().minus(retention))

        private companion object {
            const val MAX_DETAILS_LENGTH = 255
        }
    }
