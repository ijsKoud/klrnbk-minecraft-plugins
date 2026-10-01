package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiClickContext
import nl.klrnbk.minecraft.plugins.gui.common.actions.GuiActionExecutor

/** The receiver handed to `onClick { }` blocks; every shortcut simply builds the matching [GuiAction]. */
class ClickContextImpl(
    override val event: GuiClickEvent,
    private val actions: GuiActionExecutor,
) : GuiClickContext {
    override val player: Player get() = event.player
    override val gui: Gui get() = event.gui

    override fun execute(action: GuiAction) = actions.execute(action, player, event)

    override fun executePlayerCommand(command: String) = execute(GuiAction.ExecutePlayerCommand(command))

    override fun executeServerCommand(command: String) = execute(GuiAction.ExecuteServerCommand(command))

    override fun executeServerCommand(
        command: String,
        server: String,
    ) = execute(GuiAction.ExecuteServerCommand(command, server))

    override fun executeProxyCommand(command: String) = execute(GuiAction.ExecuteProxyCommand(command))

    override fun executeProxyCommand(
        command: String,
        executor: ProxyCommandExecutor,
    ) = execute(GuiAction.ExecuteProxyCommand(command, executor))

    override fun connect(server: String) = execute(GuiAction.ConnectToServer(server))

    override fun sendMessage(message: String) = execute(GuiAction.SendMessage(message))

    override fun close() = execute(GuiAction.Close)

    override fun refresh() = execute(GuiAction.Refresh)
}
