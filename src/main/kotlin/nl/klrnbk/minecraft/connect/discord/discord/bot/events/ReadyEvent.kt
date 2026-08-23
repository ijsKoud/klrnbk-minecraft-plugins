package nl.klrnbk.minecraft.connect.discord.discord.bot.events

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.connect.discord.discord.bot.commands.LinkCommand
import nl.klrnbk.minecraft.connect.discord.discord.bot.commands.LookupCommand
import org.slf4j.Logger

@Singleton
class ReadyEvent
    @Inject
    constructor(
        private val logger: Logger,
        private val lookupCommand: LookupCommand,
        private val linkCommand: LinkCommand,
    ) : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            event.jda.upsertCommand(lookupCommand.register()).queue()
            event.jda.upsertCommand(linkCommand.register()).queue()

            event.jda.presence.setPresence(OnlineStatus.ONLINE, Activity.watching("Discord & Minecraft users"))
            logger.info("Discord bot started and ready.")
        }
    }
