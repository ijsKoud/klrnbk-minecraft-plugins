package nl.klrnbk.minecraft.plugins.gui.api

/** Why a viewer stopped looking at a [Gui]. */
public enum class GuiCloseReason {
    /** The player closed the screen (Esc / inventory key / Bedrock close button). */
    CLIENT,

    /** A plugin called [Gui.close] or [Gui.closeAll]. */
    PLUGIN,

    /** The same player opened another GUI. */
    REPLACED,

    /** The backend server opened one of its own containers. */
    BACKEND_CONTAINER,

    /** The player moved to another backend server. */
    SERVER_SWITCH,

    /** The player disconnected from the proxy. */
    DISCONNECT,

    /** The framework is shutting down. */
    SHUTDOWN,
}
