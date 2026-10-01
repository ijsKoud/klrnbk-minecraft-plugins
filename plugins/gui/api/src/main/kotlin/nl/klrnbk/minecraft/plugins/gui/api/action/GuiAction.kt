package nl.klrnbk.minecraft.plugins.gui.api.action

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent

/**
 * Something that happens when a GUI item is clicked.
 *
 * The variants are named after *where* the effect happens, because a proxy has several
 * distinct places to run a command and conflating them is a classic source of bugs:
 *
 * | Action | Runs where | As whom |
 * |---|---|---|
 * | [ExecutePlayerCommand] | proxy first, else backend | the player (like typing it) |
 * | [ExecuteProxyCommand] | Velocity's command manager only | the player or the proxy console |
 * | [ExecuteServerCommand] | the player's backend server (optionally another one) | the player |
 * | [SendPluginMessage] | a backend companion plugin | (defined by the receiver) |
 *
 * **What is impossible:** neither the proxy nor a backend can make a Minecraft *client*
 * run a client-side command on its own. The only protocol mechanisms that reach the
 * client are chat messages with click events, which the player has to click — modelled
 * honestly as [SendClickableMessage]. There is deliberately no `executeClientCommand`.
 *
 * Instances are immutable and validate their input on construction, so an invalid action
 * fails where it is created, not when a player clicks it.
 */
public sealed interface GuiAction {
    /**
     * Runs a command as if the player had typed it: Velocity's command manager gets the
     * first chance (proxy commands), and if no proxy command with that name exists the
     * line is forwarded to the player's current backend server.
     */
    public class ExecutePlayerCommand(
        command: String,
    ) : GuiAction {
        public val command: String = normalizeCommand(command)

        override fun equals(other: Any?): Boolean = other is ExecutePlayerCommand && other.command == command

        override fun hashCode(): Int = command.hashCode()

        override fun toString(): String = "ExecutePlayerCommand(/$command)"
    }

    /**
     * Runs a command through Velocity's command manager only — never the backend. With
     * [ProxyCommandExecutor.PLAYER] it runs with the player's permissions, with
     * [ProxyCommandExecutor.CONSOLE] as the proxy console.
     */
    public class ExecuteProxyCommand(
        command: String,
        public val executor: ProxyCommandExecutor = ProxyCommandExecutor.PLAYER,
    ) : GuiAction {
        public val command: String = normalizeCommand(command)

        override fun equals(other: Any?): Boolean = other is ExecuteProxyCommand && other.command == command && other.executor == executor

        override fun hashCode(): Int = 31 * command.hashCode() + executor.hashCode()

        override fun toString(): String = "ExecuteProxyCommand(/$command as $executor)"
    }

    /**
     * Sends a command line to a backend server *as the player*, bypassing the proxy's own
     * commands. If [server] is set the player is connected to that server first and the command runs
     * once the connection is established; otherwise it runs on the server the player is on.
     *
     * The backend sees a normal player command, so the player needs permission for it there.
     * Running a command as the backend's *console* is not possible from a proxy without a
     * companion plugin — use [SendPluginMessage] for that.
     */
    public class ExecuteServerCommand(
        command: String,
        public val server: String? = null,
    ) : GuiAction {
        public val command: String = normalizeCommand(command)

        init {
            require(server == null || server.isNotBlank()) { "server must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is ExecuteServerCommand && other.command == command && other.server == server

        override fun hashCode(): Int = 31 * command.hashCode() + (server?.hashCode() ?: 0)

        override fun toString(): String = "ExecuteServerCommand(/$command on ${server ?: "current server"})"
    }

    /** Connects the player to a registered backend server. */
    public class ConnectToServer(
        public val server: String,
    ) : GuiAction {
        init {
            require(server.isNotBlank()) { "server must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is ConnectToServer && other.server == server

        override fun hashCode(): Int = server.hashCode()

        override fun toString(): String = "ConnectToServer($server)"
    }

    /**
     * Sends a plugin message to the player's current backend server, for a companion plugin
     * there to interpret. The channel must be `namespace:name`.
     */
    public class SendPluginMessage(
        public val channel: String,
        data: ByteArray,
    ) : GuiAction {
        private val bytes: ByteArray = data.copyOf()
        public val data: ByteArray get() = bytes.copyOf()

        init {
            require(CHANNEL.matches(channel)) { "plugin message channel must look like 'namespace:name', got '$channel'" }
        }

        override fun equals(other: Any?): Boolean = other is SendPluginMessage && other.channel == channel && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * channel.hashCode() + bytes.contentHashCode()

        override fun toString(): String = "SendPluginMessage($channel, ${bytes.size} bytes)"

        private companion object {
            val CHANNEL = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
        }
    }

    /** Sends the player a chat message (MiniMessage). */
    public class SendMessage(
        public val message: String,
    ) : GuiAction {
        override fun equals(other: Any?): Boolean = other is SendMessage && other.message == message

        override fun hashCode(): Int = message.hashCode()

        override fun toString(): String = "SendMessage($message)"
    }

    /**
     * The one honest "client-side" action: sends the player a chat message containing [text]
     * (MiniMessage) as a clickable link. **Nothing happens until the player clicks it** — the
     * proxy cannot force the client to do anything. What a click does is decided by [click].
     */
    public class SendClickableMessage(
        public val text: String,
        public val click: ClientClick,
    ) : GuiAction {
        init {
            require(text.isNotBlank()) { "text must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is SendClickableMessage && other.text == text && other.click == click

        override fun hashCode(): Int = 31 * text.hashCode() + click.hashCode()

        override fun toString(): String = "SendClickableMessage($text, $click)"
    }

    /** Opens the GUI returned by [factory] for the clicking player (replacing the current one). */
    public class OpenGui(
        public val factory: (Player) -> Gui,
    ) : GuiAction

    /** Closes the GUI the player is looking at. */
    public data object Close : GuiAction

    /** Re-renders the GUI for the clicking player. */
    public data object Refresh : GuiAction

    /** Runs arbitrary code with the click event. */
    public class Run(
        public val block: (GuiClickEvent) -> Unit,
    ) : GuiAction

    /** Runs [actions] in order; an exception in one is logged and does not stop the rest. */
    public class Composite(
        public val actions: List<GuiAction>,
    ) : GuiAction {
        public constructor(vararg actions: GuiAction) : this(actions.toList())

        override fun equals(other: Any?): Boolean = other is Composite && other.actions == actions

        override fun hashCode(): Int = actions.hashCode()

        override fun toString(): String = "Composite($actions)"
    }

    public companion object {
        /** Minecraft's chat/command packets carry at most 256 characters. */
        public const val MAX_COMMAND_LENGTH: Int = 256

        /**
         * Validates a command and strips one optional leading slash.
         *
         * @throws IllegalArgumentException if the command is blank, too long or contains
         *   control characters (which would let a crafted string smuggle extra commands).
         */
        public fun normalizeCommand(raw: String): String {
            val command = raw.trim().removePrefix("/").trim()
            require(command.isNotEmpty()) { "command must not be blank" }
            require(command.length < MAX_COMMAND_LENGTH) { "command is longer than ${MAX_COMMAND_LENGTH - 1} characters" }
            require(command.none { it.isISOControl() }) { "command must not contain control characters" }
            return command
        }
    }
}

/** Who executes an [GuiAction.ExecuteProxyCommand]. */
public enum class ProxyCommandExecutor {
    /** The clicking player, with their proxy permissions. */
    PLAYER,

    /** The proxy console. */
    CONSOLE,
}

/** What happens when a player clicks the chat line sent by [GuiAction.SendClickableMessage]. */
public sealed interface ClientClick {
    /** Puts `/command` into the player's chat box (they still have to press Enter). */
    public class SuggestCommand(
        command: String,
    ) : ClientClick {
        public val command: String = GuiAction.normalizeCommand(command)

        override fun equals(other: Any?): Boolean = other is SuggestCommand && other.command == command

        override fun hashCode(): Int = command.hashCode()
    }

    /**
     * Runs `/command` as the player. Recent clients may show a confirmation screen before
     * running commands from chat; that is client behaviour the proxy cannot change.
     */
    public class RunCommand(
        command: String,
    ) : ClientClick {
        public val command: String = GuiAction.normalizeCommand(command)

        override fun equals(other: Any?): Boolean = other is RunCommand && other.command == command

        override fun hashCode(): Int = command.hashCode()
    }

    /** Asks the client to open a URL (the client shows its own confirmation prompt). */
    public class OpenUrl(
        public val url: String,
    ) : ClientClick {
        init {
            require(url.startsWith("https://") || url.startsWith("http://")) { "url must start with http:// or https://" }
        }

        override fun equals(other: Any?): Boolean = other is OpenUrl && other.url == url

        override fun hashCode(): Int = url.hashCode()
    }

    /** Copies [text] to the player's clipboard. */
    public class CopyToClipboard(
        public val text: String,
    ) : ClientClick {
        override fun equals(other: Any?): Boolean = other is CopyToClipboard && other.text == text

        override fun hashCode(): Int = text.hashCode()
    }
}
