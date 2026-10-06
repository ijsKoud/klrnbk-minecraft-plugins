package nl.klrnbk.minecraft.plugins.discordId.common.bot

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.InteractionEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.ReadyEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.RoleChangeEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.UserRenameEvent
import nl.klrnbk.minecraft.plugins.discordId.common.facades.ScheduledTasksFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import org.slf4j.Logger

@Singleton
class BotMain
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

        fun start() {
            logger.info("Discord bot is starting...")

            discordApi = JDABuilder.createLight(configService.getConfig().discord.botToken).build()
            discordApi.addEventListener(readyEvent)
            discordApi.addEventListener(interactionEvent)
            discordApi.addEventListener(userRenameEvent)
            discordApi.addEventListener(userChangeEvent)
            scheduledTasksFacade.start(discordApi)
        }

        fun stop() {
            logger.info("Discord bot is stopping...")
            discordApi.shutdown()
            scheduledTasksFacade.stop()
        }
    }
