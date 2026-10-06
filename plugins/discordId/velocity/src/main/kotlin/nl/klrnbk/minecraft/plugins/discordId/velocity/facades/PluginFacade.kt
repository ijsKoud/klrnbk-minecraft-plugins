package nl.klrnbk.minecraft.plugins.discordId.velocity.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.key.Key
import nl.klrnbk.minecraft.packages.velocity.commands.services.CommandRegistryService
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.discordId.velocity.VelocityPlugin
import nl.klrnbk.minecraft.plugins.pkgs.i18n.TranslationService
import org.slf4j.Logger

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val botMain: BotMain,
        private val commandRegistryService: CommandRegistryService,
        private val logger: Logger,
    ) {
        private val translationService = TranslationService(Key.key("discord-id", "velocity"))

        fun start(plugin: VelocityPlugin) {
            val config = configService.load(plugin.dataDirectory)
            databaseService.start(config.database, plugin.dataDirectory)
            botMain.start()

            translationService.loadResources(javaClass.classLoader, listOf("lang/en_us.yml", "lang/nl_nl.yml"))
            commandRegistryService.register(plugin)
            logger.info("Plugin started on Velocity.")
        }

        fun stop() {
            databaseService.stop()
            botMain.stop()
            logger.info("Plugin stopped on Velocity.")
        }
    }
