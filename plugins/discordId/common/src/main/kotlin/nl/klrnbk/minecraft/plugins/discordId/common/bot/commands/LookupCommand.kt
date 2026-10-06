package nl.klrnbk.minecraft.plugins.discordId.common.bot.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import org.slf4j.Logger

@Singleton
class LookupCommand
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
        private val logger: Logger,
    ) : Command {
        override fun execute(event: SlashCommandInteractionEvent) {
            val user = event.getOption("user")?.asUser ?: return
            event.deferReply(true).queue()
            try {
                val playersName = linkFacade.getMinecraftUsernameOfDiscordUser(user.id)
                if (playersName == null) {
                    event.hook.editOriginal("${user.name} hasn't connected their Minecraft account yet.").queue()
                    return
                }

                event.hook.editOriginal(playersName).queue()
            } catch (e: Exception) {
                event.hook.editOriginal("An error occurred while looking up the Minecraft name, please try again later.").queue()
                logger.error("Error while looking up Minecraft name of user ${user.id}", e)
            }
        }

        override fun register(): CommandData =
            Commands
                .slash("lookup", "Find the Minecraft username of a Discord user.")
                .addOption(OptionType.USER, "user", "The Discord user to look up.", true)
    }
