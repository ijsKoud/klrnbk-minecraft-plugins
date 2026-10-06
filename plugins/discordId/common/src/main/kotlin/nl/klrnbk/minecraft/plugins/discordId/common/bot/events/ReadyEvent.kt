package nl.klrnbk.minecraft.plugins.discordId.common.bot.events

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LinkCommand
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LookupCommand
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import org.slf4j.Logger

@Singleton
class ReadyEvent
    @Inject
    constructor(
        private val logger: Logger,
        private val lookupCommand: LookupCommand,
        private val linkCommand: LinkCommand,
        private val configService: ConfigService,
    ) : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            val config = configService.getConfig()
            event.jda.upsertCommand(lookupCommand.register()).queue()
            event.jda.upsertCommand(linkCommand.register()).queue()

            event.jda.presence.setPresence(OnlineStatus.ONLINE, Activity.of(config.discord.statusType, config.discord.statusMessage))
            logger.info("Discord bot started and ready.")
        }
    }
