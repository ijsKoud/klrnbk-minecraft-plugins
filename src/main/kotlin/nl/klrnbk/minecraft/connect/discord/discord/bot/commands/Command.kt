package nl.klrnbk.minecraft.connect.discord.discord.bot.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface Command {
    fun execute(event: SlashCommandInteractionEvent)

    fun register(): CommandData
}
