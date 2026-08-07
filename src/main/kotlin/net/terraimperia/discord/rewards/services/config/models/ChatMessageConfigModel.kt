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

    @Setting("already-linked")
    val alreadyLinked: String =
        arrayOf(
            "<red>You are already linked to a Discord account!",
            "You can unlink your account by using the <click:suggest_command:'/unlink'><bold>/unlink</bold></click> command.</red>",
        ).joinToString("\n")

    @Setting("registration-code")
    val registrationCode: String =
        arrayOf(
            "<green>You can register your account using the following code: <link-code>",
            "Use the following command (click to copy) in our Discord server to complete the process.\n",
            "<click:coppy_to_clipboard:'/link <link-code>'><bold>/link <link-code></bold></click></green>",
        ).joinToString("\n")

    val yes: String = "<yes>Yes</yes>"
    val no: String = "<no>No</no>"
    val undefined: String = "<gray>---</gray>"
}
