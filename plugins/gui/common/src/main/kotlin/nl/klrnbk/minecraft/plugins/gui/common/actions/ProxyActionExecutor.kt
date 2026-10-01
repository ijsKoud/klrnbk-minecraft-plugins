package nl.klrnbk.minecraft.plugins.gui.common.actions

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.common.GuiManager
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Executes actions against a live Velocity proxy. See [GuiAction] for what each action means; the
 * notes here are only about *how* Velocity is driven.
 *
 * - `ExecutePlayerCommand` mirrors what Velocity does with a line the player typed: if the proxy has a
 *   command with that name it runs there, otherwise the line goes to the backend.
 * - `ExecuteServerCommand` uses [Player.spoofChatInput], which writes a chat/command packet straight to the
 *   player's backend connection and therefore *bypasses* proxy commands.
 * - When a server switch is needed first, the command is queued and only sent on the `ServerPostConnectEvent`
 *   (the backend has finished the join sequence and accepts commands). Queued commands expire so a failed
 *   connection cannot fire a command minutes later.
 */
class ProxyActionExecutor(
    private val server: ProxyServer,
    private val manager: GuiManager,
    private val logger: Logger,
    private val pendingTimeoutSeconds: Long = 15,
) : GuiActionExecutor {
    private class Pending(
        val serverName: String,
        val command: String,
        val deadlineNanos: Long,
    )

    private val pending = ConcurrentHashMap<UUID, Pending>()
    private val miniMessage = MiniMessage.miniMessage()

    override fun execute(
        action: GuiAction,
        player: Player,
        event: GuiClickEvent?,
    ) {
        try {
            perform(action, player, event)
        } catch (t: Throwable) {
            logger.error("Failed to execute GUI action {} for {}", action, player.username, t)
        }
    }

    private fun perform(
        action: GuiAction,
        player: Player,
        event: GuiClickEvent?,
    ) {
        when (action) {
            is GuiAction.ExecutePlayerCommand -> {
                val name = action.command.substringBefore(' ')
                if (server.commandManager.hasCommand(name, player)) {
                    server.commandManager.executeAsync(player, action.command)
                } else {
                    player.spoofChatInput("/${action.command}")
                }
            }

            is GuiAction.ExecuteProxyCommand -> {
                val source = if (action.executor == ProxyCommandExecutor.CONSOLE) server.consoleCommandSource else player
                server.commandManager.executeAsync(source, action.command)
            }

            is GuiAction.ExecuteServerCommand -> {
                executeServerCommand(action, player)
            }

            is GuiAction.ConnectToServer -> {
                val target = server.getServer(action.server)
                if (target.isPresent) {
                    player.createConnectionRequest(target.get()).fireAndForget()
                } else {
                    logger.warn("GUI action wants to connect {} to unknown server '{}'", player.username, action.server)
                }
            }

            is GuiAction.SendPluginMessage -> {
                val connection = player.currentServer
                if (connection.isPresent) {
                    connection.get().sendPluginMessage(MinecraftChannelIdentifier.from(action.channel), action.data)
                } else {
                    logger.warn("Cannot send plugin message {}: {} is not connected to a backend", action.channel, player.username)
                }
            }

            is GuiAction.SendMessage -> {
                player.sendMessage(miniMessage.deserialize(action.message))
            }

            is GuiAction.SendClickableMessage -> {
                player.sendMessage(clickable(action))
            }

            is GuiAction.OpenGui -> {
                action.factory(player).open(player)
            }

            GuiAction.Close -> {
                manager.currentSession(player)?.let { manager.closeSession(it, GuiCloseReason.PLUGIN, notifyClient = true) }
            }

            GuiAction.Refresh -> {
                manager.currentSession(player)?.let(manager::refresh)
            }

            is GuiAction.Run -> {
                requireNotNull(event) { "GuiAction.Run needs a click event" }
                action.block(event)
            }

            is GuiAction.Composite -> {
                action.actions.forEach { execute(it, player, event) }
            }
        }
    }

    private fun executeServerCommand(
        action: GuiAction.ExecuteServerCommand,
        player: Player,
    ) {
        val targetName = action.server
        val currentName = player.currentServer.map { it.serverInfo.name }.orElse(null)
        if (targetName == null || targetName.equals(currentName, ignoreCase = true)) {
            player.spoofChatInput("/${action.command}")
            return
        }
        val target = server.getServer(targetName)
        if (target.isEmpty) {
            logger.warn("GUI action wants to run a command on unknown server '{}'", targetName)
            return
        }
        pending[player.uniqueId] =
            Pending(target.get().serverInfo.name, action.command, System.nanoTime() + TimeUnit.SECONDS.toNanos(pendingTimeoutSeconds))
        player
            .createConnectionRequest(target.get())
            .connect()
            .thenAccept { result -> if (!result.isSuccessful) pending.remove(player.uniqueId) }
    }

    override fun onServerConnected(player: Player) {
        val waiting = pending.remove(player.uniqueId) ?: return
        val connectedTo = player.currentServer.map { it.serverInfo.name }.orElse(null)
        if (System.nanoTime() > waiting.deadlineNanos || !waiting.serverName.equals(connectedTo, ignoreCase = true)) return
        try {
            player.spoofChatInput("/${waiting.command}")
        } catch (t: Throwable) {
            logger.error("Failed to run queued server command for {}", player.username, t)
        }
    }

    override fun forget(player: UUID) {
        pending.remove(player)
    }

    private fun clickable(action: GuiAction.SendClickableMessage): Component {
        val text = miniMessage.deserialize(action.text)
        return when (val click = action.click) {
            is ClientClick.SuggestCommand -> text.clickEvent(ClickEvent.suggestCommand("/${click.command}"))
            is ClientClick.RunCommand -> text.clickEvent(ClickEvent.runCommand("/${click.command}"))
            is ClientClick.OpenUrl -> text.clickEvent(ClickEvent.openUrl(click.url))
            is ClientClick.CopyToClipboard -> text.clickEvent(ClickEvent.copyToClipboard(click.text))
        }
    }
}
