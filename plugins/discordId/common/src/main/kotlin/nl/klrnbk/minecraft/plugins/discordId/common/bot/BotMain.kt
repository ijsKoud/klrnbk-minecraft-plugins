package nl.klrnbk.minecraft.plugins.discordId.common.bot

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.InteractionEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.ReadyEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.RoleChangeEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.UserRenameEvent
import nl.klrnbk.minecraft.plugins.discordId.common.facades.ScheduledTasksFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import org.slf4j.Logger

// `open` so tests can replace start/stop: starting logs in to Discord, which tests must not do.
@Singleton
open class BotMain
    @Inject
    constructor(
        private val configService: ConfigService,
        private val logger: Logger,
        private val readyEvent: ReadyEvent,
        private val interactionEvent: InteractionEvent,
        private val userRenameEvent: UserRenameEvent,
        private val userChangeEvent: RoleChangeEvent,
        private val scheduledTasksFacade: ScheduledTasksFacade,
    ) {
        private lateinit var discordApi: JDA

        open fun start() {
            logger.info("Discord bot is starting...")

            try {
                val token = configService.getConfig().discord.botToken
                if (token?.isBlank() == true || token == "YOUR_BOT_TOKEN_HERE") {
                    logger.error("Discord bot token is not set. Please set it in the configuration file.")
                    return
                }

                discordApi =
                    JDABuilder
                        .createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MEMBERS)
                        .build()
                discordApi.addEventListener(readyEvent)
                discordApi.addEventListener(interactionEvent)
                discordApi.addEventListener(userRenameEvent)
                discordApi.addEventListener(userChangeEvent)

                scheduledTasksFacade.start(discordApi)
            } catch (e: Exception) {
                logger.error("Failed to start Discord bot", e)
            }
        }

        open fun stop() {
            logger.info("Discord bot is stopping...")

            scheduledTasksFacade.stop()
            if (::discordApi.isInitialized) discordApi.shutdown()
        }
    }
