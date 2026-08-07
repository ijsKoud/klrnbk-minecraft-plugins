package net.terraimperia.discord.rewards.services.config.models

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

@ConfigSerializable
class ChatMessageConfigModel {
    @Setting("info-message")
    val infoMessage: String =
        arrayOf(
            "<gold>▪ <bold>Link Status</bold> ▪",
            "<bold>Active</bold>: <link-status>",
            "<bold>Last Registry Date</bold>: <last-registry-date>",
            "<bold>Linked Discord ID</bold>: <linked-discord-id>",
        ).joinToString("\n")

    @Setting("player-only-command")
    val playerOnlyCommand: String = "<red>This command can only be used by players!</red>"

    val yes: String = "<yes>Yes</yes>"
    val no: String = "<no>No</no>"
    val undefined: String = "<gray>---</gray>"
}
