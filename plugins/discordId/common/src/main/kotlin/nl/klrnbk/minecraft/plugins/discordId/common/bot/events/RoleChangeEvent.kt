package nl.klrnbk.minecraft.plugins.discordId.common.bot.events

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService

@Singleton
class RoleChangeEvent
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
        private val configService: ConfigService,
    ) : ListenerAdapter() {
        override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
            val config = configService.getConfig()
            val isBoosterRole = event.roles.find { it.id == config.discord.boosterRole } != null

            if (isBoosterRole) linkFacade.updateDiscordBoosterStatusForLinkedPlayer(event.user.id, true)
        }

        override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
            val config = configService.getConfig()
            val isBoosterRole = event.roles.find { it.id == config.discord.boosterRole } != null

            if (isBoosterRole) linkFacade.updateDiscordBoosterStatusForLinkedPlayer(event.user.id, false)
        }
    }
