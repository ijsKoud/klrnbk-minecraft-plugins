package nl.klrnbk.minecraft.plugins.gui.api.item

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent

/** The receiver of `onClick { ... }`: the click event plus shortcuts for the [GuiAction]s. */
public interface GuiClickContext {
    public val event: GuiClickEvent
    public val player: Player
    public val gui: Gui

    /** Executes any [GuiAction] for the clicking player. */
    public fun execute(action: GuiAction)

    /** [GuiAction.ExecutePlayerCommand]: as the player, proxy first, then backend. */
    public fun executePlayerCommand(command: String)

    /** [GuiAction.ExecuteServerCommand] on the player's current backend server. */
    public fun executeServerCommand(command: String)

    /** [GuiAction.ExecuteServerCommand] on [server] (connecting the player first). */
    public fun executeServerCommand(
        command: String,
        server: String,
    )

    /** [GuiAction.ExecuteProxyCommand] as the player. */
    public fun executeProxyCommand(command: String)

    /** [GuiAction.ExecuteProxyCommand] as [executor]. */
    public fun executeProxyCommand(
        command: String,
        executor: ProxyCommandExecutor,
    )

    /** [GuiAction.ConnectToServer]. */
    public fun connect(server: String)

    /** [GuiAction.SendMessage]. */
    public fun sendMessage(message: String)

    /** Closes the GUI. */
    public fun close()

    /** Re-renders the GUI for this player. */
    public fun refresh()
}

/** A click handler of a [GuiItem]. */
public fun interface GuiClickHandler {
    public fun handle(context: GuiClickContext)
}
