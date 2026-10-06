package nl.klrnbk.minecraft.plugins.discordId.common.bot.events

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LinkCommand
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LookupCommand

@Singleton
class InteractionEvent
    @Inject
    constructor(
        private val lookupCommand: LookupCommand,
        private val linkCommand: LinkCommand,
    ) : ListenerAdapter() {
        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            when (event.interaction.name) {
                "lookup" -> lookupCommand.execute(event)
                "link" -> linkCommand.execute(event)
            }
        }
    }
