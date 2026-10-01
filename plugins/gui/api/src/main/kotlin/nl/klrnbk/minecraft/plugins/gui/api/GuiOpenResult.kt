package nl.klrnbk.minecraft.plugins.gui.api

/** The outcome of [Gui.open]. */
public enum class GuiOpenResult(
    public val success: Boolean,
) {
    /** The GUI is now open for the player. */
    OPENED(true),

    /** A `GuiOpenEvent` handler cancelled the opening. */
    CANCELLED(false),

    /** The player is not (or no longer) connected. */
    PLAYER_DISCONNECTED(false),

    /** The player is not connected to a backend server yet (login/configuration phase). */
    NOT_IN_PLAY_PHASE(false),

    /** The client's protocol version is older than the framework supports. */
    UNSUPPORTED_CLIENT(false),

    /** [GuiMenus.open] was asked for a menu id that is not loaded. */
    UNKNOWN_MENU(false),

    /** The player lacks the permission the YAML menu requires. */
    NO_PERMISSION(false),

    /** The protocol layer failed to send the packets (details are logged). */
    PROTOCOL_ERROR(false),
}
