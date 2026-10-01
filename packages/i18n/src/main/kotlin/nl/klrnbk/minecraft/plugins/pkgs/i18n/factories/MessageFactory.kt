package nl.klrnbk.minecraft.plugins.pkgs.i18n.factories

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.packages.common.constants.CHAT_PREFIX

class MessageFactory(
    prefix: String,
    val miniMessage: MiniMessage,
    private val component: TextComponent.Builder = Component.text(),
) {
    init {
        if (prefix.isNotEmpty()) {
            component.append(miniMessage.deserialize(prefix))
            component.appendSpace()
        }
    }

    fun appendAndParseWithMiniMessage(
        message: String,
        vararg args: ComponentLike,
    ): MessageFactory {
        component.append(miniMessage.deserialize(message), *args)
        return this
    }

    fun appendAndParseWithTranslatable(
        message: String,
        vararg args: ComponentLike,
        configure: ((TranslatableComponent) -> Component)? = null,
    ): MessageFactory {
        val translatableComponent = Component.translatable(message, *args)
        val configuredComponent = configure?.invoke(translatableComponent) ?: translatableComponent
        component.append(configuredComponent)

        return this
    }

    fun appendWithComponent(
        message: Component,
        vararg args: ComponentLike,
    ): MessageFactory {
        component.append(message, *args)
        return this
    }

    fun appendNewlines(count: Int = 1): MessageFactory {
        repeat(count) {
            component.appendNewline()
        }

        return this
    }

    fun appendText(message: String): MessageFactory {
        component.append(Component.text(message))
        return this
    }

    fun <T> appendEntries(
        entries: List<T>,
        entryMapper: (T) -> Component,
    ): MessageFactory {
        entries.forEach { entry ->
            component.append(entryMapper(entry))
        }

        return this
    }

    fun build(): TextComponent = component.build()

    companion object {
        private var prefix: String = CHAT_PREFIX

        fun factory(miniMessage: MiniMessage = MiniMessage.miniMessage()): MessageFactory = MessageFactory(prefix, miniMessage)

        fun setPrefix(prefix: String) {
            this.prefix = prefix
        }
    }
}
