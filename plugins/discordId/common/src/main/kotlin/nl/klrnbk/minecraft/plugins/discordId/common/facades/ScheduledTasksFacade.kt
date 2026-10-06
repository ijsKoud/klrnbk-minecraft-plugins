package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.JDA
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkService
import org.slf4j.Logger
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Singleton
class ScheduledTasksFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val playerLinkService: PlayerLinkService,
        private val databaseService: DatabaseService,
        private val logger: Logger,
    ) {
        private lateinit var scheduler: ScheduledExecutorService

        fun start(jda: JDA) {
            scheduler =
                java.util.concurrent.Executors
                    .newScheduledThreadPool(1)
            // We schedule a task to check for player link differences every 4 hours,
            // with an initial delay of 10 minutes, the delay is to prevent a race condition where the bot starts and checks are already started.
            // This check is to ensure that the linked players in the database are up to date with the Discord usernames and booster roles after for example an outage or downtime.
            scheduler.scheduleWithFixedDelay(
                { runSafely("player link check") { checkForPlayerLinkDifferences(jda) } },
                10,
                240,
                TimeUnit.MINUTES,
            )
            scheduler.scheduleWithFixedDelay(
                { runSafely("link code cleanup") { databaseService.performCleanup() } },
                0,
                1,
                TimeUnit.MINUTES,
            )
        }

        // internal so tests can run it directly instead of waiting for the scheduler.
        internal fun runSafely(
            name: String,
            task: () -> Unit,
        ) {
            try {
                task()
            } catch (exception: Exception) {
                logger.error("Scheduled task '$name' failed, it will run again at its next interval.", exception)
            }
        }

        fun stop() {
            scheduler.shutdown()

            try {
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
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

                boosterRole?.guild?.retrieveMemberById(it.discordId)?.queue(
                    { member ->
                        val hasBoosterRole = member.roles.any { role -> role.id == config.discord.boosterRole }
                        if (hasBoosterRole != it.isBooster) {
                            playerLinkService.updateBoosterStatusForLinkedPlayer(it.identityId, hasBoosterRole)
                        }
                    },
                    { exception ->
                        logger.error(
                            "Failed to retrieve member for Discord ID ${it.discordId} while checking for player link differences.",
                            exception,
                        )
                    },
                )
            }

            logger.debug("Finished checking for player link differences.")
        }
    }
