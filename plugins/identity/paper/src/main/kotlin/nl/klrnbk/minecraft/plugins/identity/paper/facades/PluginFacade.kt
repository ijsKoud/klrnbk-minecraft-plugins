package nl.klrnbk.minecraft.plugins.identity.paper.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.common.CHAT_PREFIX
import nl.klrnbk.minecraft.plugins.identity.common.LOGS_CLEANUP_INTERVAL
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.identity.paper.MCPluginMain
import nl.klrnbk.minecraft.plugins.identity.paper.commands.ExportCommand
import nl.klrnbk.minecraft.plugins.identity.paper.commands.ImportCommand
import nl.klrnbk.minecraft.plugins.identity.paper.commands.PlayerInformationCommand
import nl.klrnbk.minecraft.plugins.identity.paper.commands.PlayerLogsCommand
import nl.klrnbk.minecraft.plugins.identity.paper.commands.PlayerlistCommand
import nl.klrnbk.minecraft.plugins.identity.paper.commands.ReloadCommand
import nl.klrnbk.minecraft.plugins.identity.paper.listeners.PlayerConnectionListener
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger
import kotlin.time.Duration.Companion.days

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val identityApi: IdentityApi,
        private val playerConnectionListener: PlayerConnectionListener,
        private val reloadCommand: ReloadCommand,
        private val exportCommand: ExportCommand,
        private val importCommand: ImportCommand,
        private val playerlistCommand: PlayerlistCommand,
        private val playerInformationCommand: PlayerInformationCommand,
        private val playerLogsCommand: PlayerLogsCommand,
        private val logger: Logger,
    ) {
        private val translationService = TranslationService(Key.key("identity", "paper"))

        fun start(plugin: MCPluginMain) {
            val dataDirectory = plugin.dataFolder.toPath()
            val config = configService.load(dataDirectory)
            plugin.config = config

            databaseService.start(config.database, dataDirectory)
            plugin.server.pluginManager.registerEvents(playerConnectionListener, plugin)

            translationService.loadResources(javaClass.classLoader, listOf("lang/en_us.yml"))
            MessageFactory.setPrefix(prefix = CHAT_PREFIX)
            IdentityProvider.register(identityApi)

            if (config.useProxy) {
                logger.info("use-proxy is enabled; skipping command and database cleanup on Paper.")
            } else {
                registerCommands(plugin)
                plugin.server.scheduler.runTaskTimerAsynchronously(
                    plugin,
                    Runnable {
                        databaseService.performLogsCleanup(config.logs.purgeLogsAfterDays.days)
                    },
                    20L,
                    LOGS_CLEANUP_INTERVAL.toMillis() / 50L,
                )
            }

            logger.info("Plugin started on Paper.")
        }

        fun stop() {
            databaseService.stop()
            logger.info("Plugin stopped on Paper.")
        }

        private fun registerCommands(plugin: JavaPlugin) {
            reloadCommand.register(plugin)
            exportCommand.register(plugin)
            importCommand.register(plugin)
            playerlistCommand.register(plugin)
            playerInformationCommand.register(plugin)
            playerLogsCommand.register(plugin)
        }
    }
