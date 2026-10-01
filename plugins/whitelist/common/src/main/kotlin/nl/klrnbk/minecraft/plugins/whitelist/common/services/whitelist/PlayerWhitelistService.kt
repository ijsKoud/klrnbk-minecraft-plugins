package nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories.PlayerWhitelistRepository
import nl.klrnbk.minecraft.plugins.whitelist.common.services.logs.WhitelistLogsService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

@Singleton
class PlayerWhitelistService
    @Inject
    constructor(
        private val context: DatabaseContext,
        private val playerWhitelistRepository: PlayerWhitelistRepository,
        private val whitelistLogsService: WhitelistLogsService,
    ) {
        fun isPlayerWhitelisted(playerIdentityId: Uuid): Boolean {
            val whitelistDetails = playerWhitelistRepository.findByPlayerIdentityId(playerIdentityId) ?: return false
            return whitelistDetails.isWhitelisted
        }

        /**
         * @return true if the player was removed, false if the player was not whitelisted.
         */
        fun removePlayerFromWhitelist(
            playerIdentityId: Uuid,
            actorIdentityId: Uuid,
        ): Boolean = changeWhitelistStatus(playerIdentityId, actorIdentityId, false)

        /**
         * @return true if the player was added, false if the player already was whitelisted.
         */
        fun addPlayerToWhitelist(
            playerIdentityId: Uuid,
            actorIdentityId: Uuid,
        ): Boolean = changeWhitelistStatus(playerIdentityId, actorIdentityId, true)

        private fun changeWhitelistStatus(
            playerIdentityId: Uuid,
            actorIdentityId: Uuid,
            isWhitelisted: Boolean,
        ): Boolean {
            // One transaction (the repositories join it) so a change is never stored without its log entry, or the other way around.
            return transaction(context.database) {
                if (isPlayerWhitelisted(playerIdentityId) == isWhitelisted) return@transaction false

                playerWhitelistRepository.upsert(playerIdentityId, actorIdentityId, isWhitelisted)
                whitelistLogsService.logPlayerChange(playerIdentityId, actorIdentityId, isWhitelisted)
                true
            }
        }
    }
