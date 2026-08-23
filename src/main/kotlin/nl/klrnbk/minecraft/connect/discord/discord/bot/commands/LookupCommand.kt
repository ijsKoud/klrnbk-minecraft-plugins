package nl.klrnbk.minecraft.connect.discord.discord.bot.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import nl.klrnbk.minecraft.connect.discord.facade.LookupFacade

@Singleton
class LookupCommand
    @Inject
    constructor(
        private val lookupFacade: LookupFacade,
    ) : Command {
        override fun execute(event: SlashCommandInteractionEvent) {
            val user = event.getOption("user")?.asUser ?: return
            event.deferReply(true).queue()

            val playersName = lookupFacade.lookupUsersMinecraftUsername(user.idLong)
            if (playersName == null) {
                event.hook.editOriginal("${user.name} hasn't connected their Minecraft account yet.").queue()
                return
            }

            event.hook.editOriginal(playersName).queue()
        }

        override fun register(): CommandData =
            Commands
                .slash("lookup", "Find the Minecraft username of a Discord user.")
                .addOption(OptionType.USER, "user", "The Discord user to look up.", true)
    }
