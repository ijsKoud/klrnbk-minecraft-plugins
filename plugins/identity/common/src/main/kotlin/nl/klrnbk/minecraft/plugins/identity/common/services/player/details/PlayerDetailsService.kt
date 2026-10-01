package nl.klrnbk.minecraft.plugins.identity.common.services.player.details

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.transformer.fromEntity
import org.slf4j.Logger
import kotlin.uuid.Uuid

@Singleton
class PlayerDetailsService
    @Inject
    constructor(
        private val playerEntityRepository: PlayerEntityRepository,
        private val onlineStatusProvider: PlayerOnlineStatusProvider,
        private val logger: Logger,
    ) {
        fun getPlayerDetailsByPlayerId(playerId: Uuid): IdentityPlayer? {
            val playerEntity = playerEntityRepository.findByPlayerId(playerId) ?: return null

            val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerId)
            return IdentityPlayer.fromEntity(playerEntity, isPlayerOnline = isPlayerOnline)
        }

        fun getPlayerDetailsByName(playerName: String): IdentityPlayer? {
            val playerEntity = playerEntityRepository.findByName(playerName) ?: return null

            val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerEntity.playerId)
            return IdentityPlayer.fromEntity(playerEntity, isPlayerOnline = isPlayerOnline)
        }

        fun getPlayerDetailsById(id: Uuid): IdentityPlayer? {
            val playerEntity = playerEntityRepository.findById(id) ?: return null

            val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerEntity.playerId)
            return IdentityPlayer.fromEntity(playerEntity, isPlayerOnline = isPlayerOnline)
        }

        fun getAllPlayerDetails(
            page: Int,
            itemsPerPage: Int,
        ): List<IdentityPlayer> {
            val playerEntities =
                playerEntityRepository
                    .findAll(QueryPagination(page = page - 1, itemsPerPage = itemsPerPage))
                    .sortedBy { it.name.lowercase() }

            return playerEntities.map { playerEntity ->
                val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerEntity.playerId)
                IdentityPlayer.fromEntity(playerEntity, isPlayerOnline = isPlayerOnline)
            }
        }

        fun getTotalPlayerCount(): Long = playerEntityRepository.count()

        fun getAllPlayerNames(): List<String> {
            val playerEntities = playerEntityRepository.findAll(QueryPagination(page = 0, itemsPerPage = Int.MAX_VALUE))
            return playerEntities.map { it.name }.sorted()
        }

        fun upsert(
            playerId: Uuid,
            playerName: String,
        ): IdentityPlayer {
            val playerEntity = playerEntityRepository.findByPlayerId(playerId)
            if (playerEntity != null) {
                if (playerEntity.name != playerName) updatePlayerName(playerId, playerName)
                val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerId)

                return IdentityPlayer.fromEntity(playerEntity, isPlayerOnline = isPlayerOnline, updatedName = playerName)
            }

            val newPlayerEntity = playerEntityRepository.create(playerId, playerName)
            val isPlayerOnline = onlineStatusProvider.isPlayerOnline(playerId)

            logger.info("Created new player entity for playerId={} with name={}", playerId, playerName)
            return IdentityPlayer.fromEntity(newPlayerEntity, isPlayerOnline = isPlayerOnline)
        }

        private fun updatePlayerName(
            playerId: Uuid,
            newName: String,
        ) {
            val succeeded = playerEntityRepository.updateNameByPlayerId(playerId, newName)
            if (!succeeded) {
                logger.warn("Failed to update player name for playerId={}", playerId)
            } else {
                logger.debug("Updated player name for playerId={} to {}", playerId, newName)
            }
        }
    }
