package nl.klrnbk.minecraft.connect.discord.discord.bot

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import nl.klrnbk.minecraft.connect.discord.discord.bot.commands.LookupCommand
import nl.klrnbk.minecraft.connect.discord.discord.bot.events.InteractionEvent
import nl.klrnbk.minecraft.connect.discord.discord.bot.events.ReadyEvent
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService
import org.slf4j.Logger

@Singleton
class Bot
    @Inject
    constructor(
        private val configService: ConfigService,
        private val logger: Logger,
        private val readyEvent: ReadyEvent,
        private val interactionEvent: InteractionEvent,
    ) {
        private lateinit var discordApi: JDA

        fun start() {
            logger.info("Discord bot is starting...")

            discordApi = JDABuilder.createLight(configService.config.registration.discordBotToken).build()
            discordApi.addEventListener(readyEvent)
            discordApi.addEventListener(interactionEvent)
        }
    }
