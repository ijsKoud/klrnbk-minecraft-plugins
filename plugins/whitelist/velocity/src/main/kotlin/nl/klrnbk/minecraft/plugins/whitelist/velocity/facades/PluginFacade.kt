package nl.klrnbk.minecraft.plugins.whitelist.velocity.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.packages.velocity.commands.services.CommandRegistryService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.whitelist.common.CHAT_PREFIX
import nl.klrnbk.minecraft.plugins.whitelist.common.LOGS_CLEANUP_INTERVAL
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.velocity.VelocityPlugin
import nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners.PlayerConnectListener
import org.slf4j.Logger
import kotlin.time.Duration.Companion.days

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val activeStatusService: ActiveStatusService,
        private val commandRegistryService: CommandRegistryService,
        private val playerConnectListener: PlayerConnectListener,
        private val logger: Logger,
    ) {
        private val translationService = TranslationService(Key.key("whitelist", "velocity"))

        fun start(plugin: VelocityPlugin) {
            val config = configService.load(plugin.dataDirectory)
            activeStatusService.start(plugin.dataDirectory)
            databaseService.start(config.database, plugin.dataDirectory)

            plugin.server.scheduler
                .buildTask(
                    plugin,
                    Runnable {
                        try {
                            // Read at run time so a changed retention applies after a reload.
                            databaseService.performLogsCleanup(configService.getConfig().logs.purgeLogsAfterDays.days)
                        } catch (exception: Exception) {
                            logger.error("Failed to clean up the whitelist logs.", exception)
                        }
                    },
                ).repeat(LOGS_CLEANUP_INTERVAL)
                .schedule()

            translationService.loadResources(javaClass.classLoader, listOf("lang/en_us.yml"))
            MessageFactory.setPrefix(prefix = CHAT_PREFIX)

            commandRegistryService.register(plugin)
            plugin.server.eventManager.register(plugin, playerConnectListener)

            logger.info("Plugin started on Velocity.")
        }

        fun stop() {
            translationService.close()
            databaseService.stop()
            logger.info("Plugin stopped on Velocity.")
        }
    }
