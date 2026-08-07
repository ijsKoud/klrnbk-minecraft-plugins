package net.terraimperia.discord.rewards.services.minimessage

import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

@Singleton
class MiniMessageService {
    private val miniMessage = MiniMessage.miniMessage()

    fun deserialize(
        message: String,
        vararg placeholders: TagResolver,
    ) = miniMessage.deserialize(message, *placeholders)

    fun serialize(component: Component) = miniMessage.serialize(component)
}
