package nl.klrnbk.minecraft.plugins.pkgs.i18n.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter
import java.time.LocalDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

fun instantToComponentText(
    miniMessage: MiniMessage,
    instant: Instant,
): Component =
    miniMessage.deserialize(
        "<date:'yyyy-MM-dd HH:mm:ss'>",
        Formatter.date("date", LocalDateTime.ofInstant(instant.toJavaInstant(), java.time.ZoneId.systemDefault())),
    )
