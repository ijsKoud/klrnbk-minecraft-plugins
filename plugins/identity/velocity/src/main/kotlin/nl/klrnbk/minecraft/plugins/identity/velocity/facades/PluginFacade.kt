package nl.klrnbk.minecraft.plugins.identity.velocity.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.Runnable
import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.packages.velocity.commands.services.CommandRegistryService
import nl.klrnbk.minecraft.plugins.identity.common.CHAT_PREFIX
import nl.klrnbk.minecraft.plugins.identity.common.LOGS_CLEANUP_INTERVAL
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import nl.klrnbk.minecraft.plugins.identity.velocity.VelocityPlugin
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val playerDetailsService: PlayerDetailsService,
        private val playerConnectionLogsService: PlayerConnectionLogsService,
        private val commandRegistryService: CommandRegistryService,
        private val logger: Logger,
    ) {
        private val translationService = TranslationService(Key.key("identity", "velocity"))

        fun start(plugin: VelocityPlugin) {
            val config = configService.load(plugin.dataDirectory)
            databaseService.start(config.database, plugin.dataDirectory)

            plugin.server.scheduler
                .buildTask(
                    plugin,
                    Runnable {
                        val retentionDuration = config.logs.purgeLogsAfterDays.days
                        databaseService.performLogsCleanup(retentionDuration)
                    },
                ).repeat(LOGS_CLEANUP_INTERVAL)
                .schedule()

            translationService.loadResources(javaClass.classLoader, listOf("lang/en_us.yml", "lang/nl_nl.yml"))
            MessageFactory.setPrefix(prefix = CHAT_PREFIX)
            commandRegistryService.register(plugin)
            logger.info("Plugin started on Velocity.")
        }

        fun stop() {
            databaseService.stop()
            logger.info("Plugin stopped on Velocity.")
        }

        fun registerAndOrLogPlayerConnection(
            playerId: Uuid,
            playerName: String,
            serverIp: String,
            playerIp: String,
        ) {
            playerDetailsService.upsert(
                playerId = playerId,
                playerName = playerName,
            )

            val config = configService.getConfig()
            if (!config.logs.enabled) return

            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)
            playerConnectionLogsService.addConnectLogEntry(
                playerId = playerId,
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )
        }

        fun logPlayerDisconnection(
            playerId: Uuid,
            serverIp: String,
            playerIp: String,
        ) {
            val config = configService.getConfig()
            if (!config.logs.enabled) return

            // A player that left before the login event reached us was never registered, there is nothing to log.
            if (playerDetailsService.getPlayerDetailsByPlayerId(playerId) == null) {
                logger.debug("Not logging the disconnect of unregistered player {}", playerId)
                return
            }

            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)
            playerConnectionLogsService.addDisconnectLogEntry(
                playerId = playerId,
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )
        }

        fun logPlayerJoinServer(
            playerId: Uuid,
            serverName: String,
            serverIp: String,
            playerIp: String,
        ) {
            val config = configService.getConfig()
            if (!config.logs.enabled) return

            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)
            playerConnectionLogsService.addJoinServerLogEntry(
                playerId = playerId,
                serverName = serverName,
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )
        }

        fun logPlayerLeaveServer(
            playerId: Uuid,
            serverName: String,
            serverIp: String,
            playerIp: String,
        ) {
            val config = configService.getConfig()
            if (!config.logs.enabled) return

            val encryptedPlayerIp = CryptographyUtil.encrypt(playerIp, config.encryptionKey!!)
            playerConnectionLogsService.addLeaveServerLogEntry(
                playerId = playerId,
                serverName = serverName,
                serverIp = serverIp,
                playerIp = if (config.logs.logIps) encryptedPlayerIp else null,
                encryptionKey = config.encryptionKey!!,
            )
        }
    }
