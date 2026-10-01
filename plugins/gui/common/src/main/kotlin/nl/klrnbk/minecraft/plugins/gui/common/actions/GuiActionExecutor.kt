package nl.klrnbk.minecraft.plugins.gui.common.actions

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import java.util.UUID

/** Performs [GuiAction]s on behalf of a player. */
interface GuiActionExecutor {
    /** [event] is the click that triggered the action, or `null` when an action is run programmatically. */
    fun execute(
        action: GuiAction,
        player: Player,
        event: GuiClickEvent?,
    )

    /** A player finished connecting to a backend: run commands that were waiting for it. */
    fun onServerConnected(player: Player)

    fun forget(player: UUID)
}
