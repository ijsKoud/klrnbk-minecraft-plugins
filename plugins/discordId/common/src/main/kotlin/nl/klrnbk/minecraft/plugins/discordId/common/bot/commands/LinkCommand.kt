package nl.klrnbk.minecraft.plugins.discordId.common.bot.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService

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

            val config = configService.getConfig()
            val isBooster = event.member?.roles?.find { it.id == config.discord.boosterRole } != null
            val result = linkFacade.linkPlayer(code, event.user.id, event.user.name, isBooster)

            event.hook.editOriginal(result).queue()
        }

        override fun register(): CommandData =
            Commands
                .slash("link", "Link your Discord and Minecraft accounts.")
                .addOption(OptionType.STRING, "code", "The code you received in Minecraft to link your account.", true)
    }
