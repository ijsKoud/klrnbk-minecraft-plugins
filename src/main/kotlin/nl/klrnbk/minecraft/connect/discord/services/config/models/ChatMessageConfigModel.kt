package nl.klrnbk.minecraft.connect.discord.services.config.models

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
            "<click:copy_to_clipboard:'/link <link-code>'><bold>/link <link-code></bold></click></green>",
        ).joinToString("\n")

    @Setting("discord-not-linked")
    val notLinked: String =
        arrayOf(
            "<red>You have not linked to any Discord account yet!",
            " Start by linking using the <click:suggest_command:'/link'><bold>/link</bold></click> command.</red>",
        ).joinToString("\n")

    @Setting("unlink-too-soon")
    val unlinkTooSoon: String =
        arrayOf(
            "<red>You cannot unlink your account yet!",
            "Please wait until <white><date></white> before trying again.</red>",
        ).joinToString("\n")

    @Setting("unlinked")
    val unlinked: String =
        arrayOf(
            "<green>Your account has been successfully unlinked from Discord!",
            "Want to link it again? Use the <click:suggest_command:'/link'><bold>/link</bold></click> command</green>",
        ).joinToString("\n")

    @Setting("player-has-not-linked-discord")
    val noDiscordLinked: String =
        arrayOf(
            "<red>This player hasn't linked his Discord yet.</red>",
        ).joinToString("\n")

    @Setting("player-does-not-exist")
    val playerDoesNotExist: String =
        arrayOf(
            "<red>Could not find this player, make sure they have been on the server at least once.</red>",
        ).joinToString("\n")

    @Setting("lookup-result")
    val lookupResult: String =
        arrayOf(
            "The Discord username for <bold><minecraft-name></bold> is <bold><discord-username></bold>.",
        ).joinToString("\n")

    val yes: String = "<yes>Yes</yes>"
    val no: String = "<no>No</no>"
    val undefined: String = "<gray>---</gray>"
}
