package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistLog
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistSettingsLog
import nl.klrnbk.minecraft.plugins.whitelist.common.services.logs.WhitelistLogsService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import java.util.UUID
import kotlin.uuid.toKotlinUuid

@Singleton
class WhitelistApiFacade
    @Inject
    constructor(
        private val playerWhitelistService: PlayerWhitelistService,
        private val activeStatusService: ActiveStatusService,
        private val whitelistLogsService: WhitelistLogsService,
    ) : WhitelistApi {
        override fun isPlayerWhitelisted(identityId: UUID): Boolean = playerWhitelistService.isPlayerWhitelisted(identityId.toKotlinUuid())

        override fun isWhitelistEnabled(): Boolean = activeStatusService.isWhitelistEnabled()

        override fun setWhitelistEnabled(
            enabled: Boolean,
            actorIdentityId: UUID,
        ): Boolean = activeStatusService.setWhitelistEnabled(enabled, actorIdentityId.toKotlinUuid())

        override fun addPlayerToWhitelist(
            identityId: UUID,
            actorIdentityId: UUID,
        ): Boolean = playerWhitelistService.addPlayerToWhitelist(identityId.toKotlinUuid(), actorIdentityId.toKotlinUuid())

        override fun removePlayerFromWhitelist(
            identityId: UUID,
            actorIdentityId: UUID,
        ): Boolean = playerWhitelistService.removePlayerFromWhitelist(identityId.toKotlinUuid(), actorIdentityId.toKotlinUuid())

        override fun getPlayerLogs(
            identityId: UUID,
            page: Int,
            itemsPerPage: Int,
        ): List<WhitelistLog> = whitelistLogsService.getPlayerLogs(identityId.toKotlinUuid(), pagination(page, itemsPerPage))

        override fun getPlayerLogsCount(identityId: UUID): Long = whitelistLogsService.getPlayerLogsCount(identityId.toKotlinUuid())

        override fun getSettingsLogs(
            page: Int,
            itemsPerPage: Int,
        ): List<WhitelistSettingsLog> = whitelistLogsService.getSettingsLogs(pagination(page, itemsPerPage))

        override fun getSettingsLogsCount(): Long = whitelistLogsService.getSettingsLogsCount()

        private fun pagination(
            page: Int,
            itemsPerPage: Int,
        ): QueryPagination {
            require(page >= 0) { "page must not be negative" }
            require(itemsPerPage > 0) { "itemsPerPage must be positive" }

            return QueryPagination(page, itemsPerPage.coerceAtMost(MAX_ITEMS_PER_PAGE))
        }

        private companion object {
            const val MAX_ITEMS_PER_PAGE = 100
        }
    }
