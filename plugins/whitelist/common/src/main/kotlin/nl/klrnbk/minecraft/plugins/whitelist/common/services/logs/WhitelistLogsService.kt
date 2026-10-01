package nl.klrnbk.minecraft.plugins.whitelist.common.services.logs

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistLog
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistSettingsLog
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistLogEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.WhitelistSettingsLogsEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.WhitelistLogEntityRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.WhitelistSettingsLogsEntityRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Singleton
class WhitelistLogsService
    @Inject
    constructor(
        private val whitelistLogEntityRepository: WhitelistLogEntityRepository,
        private val whitelistSettingsLogsEntityRepository: WhitelistSettingsLogsEntityRepository,
        private val configService: ConfigService,
    ) {
        /**
         * Logs that [playerIdentityId] was added to or removed from the whitelist by [actorIdentityId].
         * Does nothing when logging is disabled in the config.
         */
        fun logPlayerChange(
            playerIdentityId: Uuid,
            actorIdentityId: Uuid,
            isWhitelisted: Boolean,
        ) {
            if (!configService.getConfig().logs.enabled) return
            whitelistLogEntityRepository.create(playerIdentityId, actorIdentityId, isWhitelisted)
        }

        /**
         * Logs that the whitelist was toggled by [actorIdentityId].
         * Does nothing when logging is disabled in the config.
         */
        fun logWhitelistToggle(
            actorIdentityId: Uuid,
            isWhitelistEnabled: Boolean,
        ) {
            if (!configService.getConfig().logs.enabled) return
            whitelistSettingsLogsEntityRepository.create(actorIdentityId, isWhitelistEnabled)
        }

        fun getPlayerLogs(
            playerIdentityId: Uuid,
            pagination: QueryPagination,
        ): List<WhitelistLog> = whitelistLogEntityRepository.findAllByIdentityId(playerIdentityId, pagination).map { it.toApiModel() }

        fun getPlayerLogsCount(playerIdentityId: Uuid): Long = whitelistLogEntityRepository.count(playerIdentityId)

        fun getSettingsLogs(pagination: QueryPagination): List<WhitelistSettingsLog> =
            whitelistSettingsLogsEntityRepository.findAll(pagination).map { it.toApiModel() }

        fun getSettingsLogsCount(): Long = whitelistSettingsLogsEntityRepository.count()

        /**
         * Deletes all logs older than [retention].
         *
         * @return the number of deleted logs.
         */
        fun deleteLogsOlderThan(retention: Duration): Int {
            val cutoff = Clock.System.now().minus(retention)
            return whitelistLogEntityRepository.deleteBefore(cutoff) + whitelistSettingsLogsEntityRepository.deleteBefore(cutoff)
        }

        private fun WhitelistLogEntity.toApiModel() =
            WhitelistLog(
                id = id.value.toJavaUuid(),
                playerId = identityId.toJavaUuid(),
                actorId = actorIdentityId.toJavaUuid(),
                isWhitelisted = isWhitelisted,
                timestamp = timestamp.toJavaInstant(),
            )

        private fun WhitelistSettingsLogsEntity.toApiModel() =
            WhitelistSettingsLog(
                id = id.value.toJavaUuid(),
                actorId = playerIdentityId.toJavaUuid(),
                isWhitelistEnabled = isWhitelistEnabled,
                timestamp = timestamp.toJavaInstant(),
            )
    }
