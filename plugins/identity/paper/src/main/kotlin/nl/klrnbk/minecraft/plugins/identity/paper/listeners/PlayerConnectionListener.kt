package nl.klrnbk.minecraft.plugins.identity.paper.listeners

import com.google.inject.Inject
import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import org.bukkit.Server
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.slf4j.Logger
import kotlin.uuid.toKotlinUuid

class PlayerConnectionListener
    @Inject
    constructor(
        private val configService: ConfigService,
        private val playerDetailsService: PlayerDetailsService,
        private val playerConnectionLogsService: PlayerConnectionLogsService,
        private val server: Server,
        private val logger: Logger,
    ) : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
            val config = configService.getConfig()
            if (config.useProxy) return

            playerDetailsService.upsert(playerId = event.uniqueId.toKotlinUuid(), playerName = event.name)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val config = configService.getConfig()
            val playerId = event.player.uniqueId.toKotlinUuid()

            if (!config.logs.enabled) {
                return
            }

            val playerIp =
                event.player.address
                    ?.address
                    ?.hostAddress ?: "unknown"
            val serverIp = if (server.ip.isBlank()) "unknown" else "${server.ip}:${server.port}"
            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)

            playerConnectionLogsService.addConnectLogEntry(
                playerId = playerId,
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )

            logger.info("Player {} joined Paper.", event.player.name)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            val config = configService.getConfig()
            if (!config.logs.enabled) {
                return
            }

            val playerIp =
                event.player.address
                    ?.address
                    ?.hostAddress ?: "unknown"
            val serverIp = if (server.ip.isBlank()) "unknown" else "${server.ip}:${server.port}"
            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)

            playerConnectionLogsService.addDisconnectLogEntry(
                playerId = event.player.uniqueId.toKotlinUuid(),
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )

            logger.info("Player {} left Paper.", event.player.name)
        }
    }
