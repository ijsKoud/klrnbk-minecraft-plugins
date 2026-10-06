package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.JDA
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkService
import org.slf4j.Logger

@Singleton
class ScheduledTasksFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val playerLinkService: PlayerLinkService,
        private val databaseService: DatabaseService,
        private val logger: Logger,
    ) {
        private val scheduler =
            java.util.concurrent.Executors
                .newScheduledThreadPool(1)

        fun start(jda: JDA) {
            // We schedule a task to check for player link differences every 4 hours,
            // with an initial delay of 10 minutes, the delay is to prevent a race condition where the bot starts and checks are already started.
            // This check is to ensure that the linked players in the database are up to date with the Discord usernames and booster roles after for example an outage or downtime.
            scheduler.scheduleWithFixedDelay(
                { checkForPlayerLinkDifferences(jda) },
                10,
                240,
                java.util.concurrent.TimeUnit.MINUTES,
            )

            scheduler.scheduleWithFixedDelay(
                { databaseService.performCleanup() },
                0,
                1,
                java.util.concurrent.TimeUnit.MINUTES,
            )
        }

        fun stop() {
            scheduler.shutdown()

            try {
                if (!scheduler.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                scheduler.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        fun checkForPlayerLinkDifferences(jda: JDA) {
            val config = configService.getConfig()
            val allLinkedPlayers = playerLinkService.getAllLinkedPlayers()

            val boosterRole =
                if (config.discord.boosterRole != null) {
                    jda.getRoleById(config.discord.boosterRole)
                } else {
                    null
                }

            logger.debug("Checking for player link differences for ${allLinkedPlayers.size} linked players.")
            allLinkedPlayers.filter { !it.discordId.isNullOrEmpty() }.forEach {
                jda.getUserById(it.discordId!!)?.let { user ->
                    if (user.name != it.discordName) {
                        playerLinkService.updateDiscordUsernameForLinkedPlayer(it.identityId, user.name)
                    }
                }

                boosterRole?.guild?.getMemberById(it.discordId)?.let { member ->
                    val hasBoosterRole = member.roles.any { role -> role.id == config.discord.boosterRole }
                    if (hasBoosterRole != it.isBooster) {
                        playerLinkService.updateBoosterStatusForLinkedPlayer(it.identityId, hasBoosterRole)
                    }
                }
            }
            logger.debug("Finished checking for player link differences.")
        }
    }
