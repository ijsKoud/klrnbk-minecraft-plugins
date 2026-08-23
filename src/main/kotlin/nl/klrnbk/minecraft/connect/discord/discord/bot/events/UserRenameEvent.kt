package nl.klrnbk.minecraft.connect.discord.discord.bot.events

import com.google.inject.Inject
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import nl.klrnbk.minecraft.connect.discord.facade.LinkFacade

class UserRenameEvent
    @Inject
    constructor(
        private val linkFacade: LinkFacade,
    ) : ListenerAdapter() {
        override fun onUserUpdateName(event: UserUpdateNameEvent) {
            val newName = event.newName
        }
    }
