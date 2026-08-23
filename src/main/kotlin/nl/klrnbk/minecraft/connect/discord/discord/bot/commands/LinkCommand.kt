package nl.klrnbk.minecraft.connect.discord.discord.bot.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import nl.klrnbk.minecraft.connect.discord.facade.LinkFacade
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService

@Singleton
class LinkCommand
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
        private val configService: ConfigService,
    ) : Command {
        override fun execute(event: SlashCommandInteractionEvent) {
            val code = event.getOption("code")?.asString ?: return
            event.deferReply(true).queue()

            val isBooster = event.member?.roles?.find { it.idLong == configService.config.registration.boosterRole } != null
            val result = linkFacade.linkDiscord(event.user.name, event.user.idLong, isBooster, code)
            if (!result) {
                event.hook.editOriginal("Unable to link your account. Please try again later!").queue()
                return
            }

            event.hook.editOriginal("Account linked").queue()
        }

        override fun register(): CommandData =
            Commands
                .slash("link", "Link your Discord and Minecraft accounts.")
                .addOption(OptionType.STRING, "code", "The code you received in Minecraft to link your account.", true)
    }
