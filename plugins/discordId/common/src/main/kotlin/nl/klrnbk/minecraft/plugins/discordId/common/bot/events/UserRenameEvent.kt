package nl.klrnbk.minecraft.plugins.discordId.common.bot.events

import com.google.inject.Inject
import com.google.inject.Singleton
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade

@Singleton
class UserRenameEvent
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
    ) : ListenerAdapter() {
        override fun onUserUpdateName(event: UserUpdateNameEvent) {
            val newName = event.newName
            val userId = event.user.id
            linkFacade.updateDiscordNameForLinkedPlayer(userId, newName)
        }
    }
