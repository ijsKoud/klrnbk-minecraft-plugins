package nl.klrnbk.minecraft.plugins.discordId.common

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.InteractionEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.ReadyEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.RoleChangeEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.UserRenameEvent
import nl.klrnbk.minecraft.plugins.discordId.common.facades.ScheduledTasksFacade
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import org.slf4j.Logger

/**
 * The translatable message inside a facade/command result (those are a prefix followed by the message).
 */
fun TextComponent.translatable(): TranslatableComponent = children().filterIsInstance<TranslatableComponent>().single()

fun TextComponent.messageKey(): String = translatable().key()

/**
 * The arguments of the message inside a facade/command result.
 */
fun TextComponent.messageArguments(): List<Component> = translatable().arguments().map { it.asComponent() }

/**
 * A [BotMain] that records start/stop instead of logging in to Discord.
 */
class RecordingBotMain(
    configService: ConfigService,
    logger: Logger,
    readyEvent: ReadyEvent,
    interactionEvent: InteractionEvent,
    userRenameEvent: UserRenameEvent,
    roleChangeEvent: RoleChangeEvent,
    scheduledTasksFacade: ScheduledTasksFacade,
) : BotMain(configService, logger, readyEvent, interactionEvent, userRenameEvent, roleChangeEvent, scheduledTasksFacade) {
    var starts = 0
        private set
    var stops = 0
        private set

    override fun start() {
        starts++
    }

    override fun stop() {
        stops++
    }
}
