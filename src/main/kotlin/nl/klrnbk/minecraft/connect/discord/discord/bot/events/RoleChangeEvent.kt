package nl.klrnbk.minecraft.connect.discord.discord.bot.events

import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class RoleChangeEvent : ListenerAdapter() {
    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        // Handle role add event
    }

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
        // Handle role remove event
    }
}
