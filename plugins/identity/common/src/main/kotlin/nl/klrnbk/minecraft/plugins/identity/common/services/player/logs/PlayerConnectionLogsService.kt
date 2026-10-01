package nl.klrnbk.minecraft.plugins.identity.common.services.player.logs

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogs
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.transformer.fromEntity
import org.slf4j.Logger
import javax.crypto.SecretKey
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerConnectionLogsService
    @Inject
    constructor(
        private val playerEntityRepository: PlayerEntityRepository,
        private val playerConnectionLogEntityRepository: PlayerConnectionLogEntityRepository,
        private val logger: Logger,
    ) {
        fun getLogsCountForPlayer(id: Uuid): Long = playerConnectionLogEntityRepository.count(id)

        fun addLogEntry(
            playerId: Uuid,
            type: ConnectionEventType,
            serverIp: String,
            serverName: String,
            playerIp: String?,
            encryptionKey: SecretKey,
        ): IdentityPlayerLogs {
            val playerEntity =
                playerEntityRepository.findByPlayerId(playerId)
                    ?: throw IllegalArgumentException("Player not found but should be registered, playerId=$playerId")

            logger.info(
                "Adding log entry for player=$playerId, type=$type, serverIp=$serverIp, serverName=$serverName, timestamp=${Clock.System.now()}",
            )
            val entity =
                playerConnectionLogEntityRepository.create(
                    playerId = playerEntity.id.value,
                    eventType = type,
                    serverIp = serverIp,
                    serverName = serverName,
                    playerIp = playerIp,
                )

            return IdentityPlayerLogs.fromEntity(entity, encryptionKey)
        }

        fun addConnectLogEntry(
            playerId: Uuid,
            serverIp: String,
            playerIp: String?,
            encryptionKey: SecretKey,
        ): IdentityPlayerLogs =
            addLogEntry(
                playerId = playerId,
                type = ConnectionEventType.CONNECT_TO_PROXY,
                serverIp = serverIp,
                serverName = "proxy",
                playerIp = playerIp,
                encryptionKey = encryptionKey,
            )

        fun addJoinServerLogEntry(
            playerId: Uuid,
            serverName: String,
            serverIp: String,
            playerIp: String?,
            encryptionKey: SecretKey,
        ): IdentityPlayerLogs =
            addLogEntry(
                playerId = playerId,
                type = ConnectionEventType.JOIN_SERVER,
                serverIp = serverIp,
                serverName = serverName,
                playerIp = playerIp,
                encryptionKey = encryptionKey,
            )

        fun addDisconnectLogEntry(
            playerId: Uuid,
            serverIp: String,
            playerIp: String?,
            encryptionKey: SecretKey,
        ): IdentityPlayerLogs =
            addLogEntry(
                playerId = playerId,
                type = ConnectionEventType.DISCONNECT_FROM_PROXY,
                serverIp = serverIp,
                serverName = "proxy",
                playerIp = playerIp,
                encryptionKey = encryptionKey,
            )

        fun addLeaveServerLogEntry(
            playerId: Uuid,
            serverIp: String,
            serverName: String,
            playerIp: String?,
            encryptionKey: SecretKey,
        ): IdentityPlayerLogs =
            addLogEntry(
                playerId = playerId,
                type = ConnectionEventType.LEAVE_SERVER,
                serverIp = serverIp,
                serverName = serverName,
                playerIp = playerIp,
                encryptionKey = encryptionKey,
            )

        /**
         * Gets the logs for a player, paginated.
         * @param playerId The UUID of the player to get logs for. This being the INTERNAL UUID of the player, not the Mojang UUID.
         * @param page The page number to get logs for. Starts at 0.
         * @param pageSize The number of logs to get per page.
         * @return A list of IdentityPlayerLogs for the player, paginated.
         */
        fun getLogsForPlayer(
            playerId: Uuid,
            page: Int,
            pageSize: Int,
            encryptionKey: SecretKey,
        ): List<IdentityPlayerLogs> {
            val pagination = QueryPagination(page = page, itemsPerPage = pageSize)
            val logs = playerConnectionLogEntityRepository.findAllByPlayerId(playerId, pagination)

            return logs.map { IdentityPlayerLogs.fromEntity(it, encryptionKey) }
        }
    }
