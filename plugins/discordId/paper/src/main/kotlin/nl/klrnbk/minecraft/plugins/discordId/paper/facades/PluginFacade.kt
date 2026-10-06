package nl.klrnbk.minecraft.plugins.discordId.paper.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.discordId.paper.MCPluginMain
import nl.klrnbk.minecraft.plugins.discordId.paper.commands.DiscordIdCommand
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import org.slf4j.Logger

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val botMain: BotMain,
        private val discordIdCommand: DiscordIdCommand,
        private val logger: Logger,
    ) {
        private val translationService = TranslationService(Key.key("discord-id", "paper"))
        private var botStarted = false

        fun start(plugin: MCPluginMain) {
            val dataDirectory = plugin.dataFolder.toPath()
            val config = configService.load(dataDirectory)
            databaseService.start(config.database, dataDirectory)

            translationService.loadResources(javaClass.classLoader, listOf("lang/en_us.yml"))

            if (config.useProxy) {
                // The proxy runs the bot (a token can only be connected once) and the commands.
                logger.info("use-proxy is enabled; skipping the Discord bot and commands on Paper.")
            } else {
                botMain.start()
                botStarted = true
                discordIdCommand.register(plugin)
            }

            logger.info("Plugin started on Paper.")
        }

        fun stop() {
            // The bot first: its scheduled tasks use the database.
            if (botStarted) {
                botMain.stop()
                botStarted = false
            }
            databaseService.stop()
            translationService.close()
            logger.info("Plugin stopped on Paper.")
        }
    }
