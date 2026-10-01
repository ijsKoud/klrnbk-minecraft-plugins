package nl.klrnbk.minecraft.plugins.gui.example

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiApi
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.StateKey
import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem

/**
 * How another KLRNBK plugin uses the GUI framework. Everything here compiles against `gui:api` only — no packet,
 * PacketEvents or Geyser type appears, so a Minecraft protocol upgrade inside `klrnbk-gui` never touches this file.
 *
 * The menu doubles as the manual test harness described in TESTING.md: every row exercises one feature.
 */
class ExampleMenu(
    private val guiApi: GuiApi,
    private val proxy: ProxyServer,
) {
    private val lastClick = StateKey<String>("last-click")
    private val counter = StateKey<Int>("counter")

    /** One shared GUI instance; per-viewer differences come from dynamic slots and per-viewer state. */
    private val menu: Gui = build()

    fun open(player: Player) {
        val result = menu.open(player)
        if (!result.success) player.sendMessage(net.kyori.adventure.text.Component.text("Could not open the menu: $result"))
    }

    private fun build(): Gui {
        val gui = guiApi.create(title = "<dark_blue>KLRNBK <gray>Menu", rows = 3)

        // 1. A plain item with a display name, lore and a server command.
        gui.setItem(
            10,
            guiItem {
                material = Material.DIAMOND
                name = "<aqua>Spawn"
                lore(
                    "<gray>Teleport to spawn",
                    "",
                    "<yellow>Click to teleport",
                )
                onClick {
                    executeServerCommand("spawn")
                    close()
                }
            },
        )

        // 2. A proxy command (runs in Velocity, never on the backend), as the console.
        gui.setItem(
            11,
            guiItem {
                material = Material.COMPASS
                name = "<green>Proxy: /glist"
                lore("<gray>Runs a Velocity command", "<gray>as the proxy console")
                onClick { executeProxyCommand("glist", ProxyCommandExecutor.CONSOLE) }
            },
        )

        // 3. Connecting to another server.
        gui.setItem(
            12,
            guiItem {
                material = Material.ENDER_PEARL
                name = "<light_purple>Go to lobby"
                lore("<gray>Left click: connect", "<gray>Right click: connect and run /spawn there")
                onLeftClick { connect("lobby") }
                onRightClick { executeServerCommand("spawn", server = "lobby") }
            },
        )

        // 4. The honest "client side" action: a clickable chat message. Nothing runs until the player clicks it.
        gui.setItem(
            13,
            guiItem {
                material = Material.WRITABLE_BOOK
                name = "<gold>Suggest a command"
                lore("<gray>Sends a chat message; clicking it", "<gray>puts <white>/warp <gray>in your chat box")
                action(GuiAction.SendClickableMessage("<yellow>[Click to prepare /warp]", ClientClick.SuggestCommand("warp")))
            },
        )

        // 5. Dynamic item: differs per viewer and re-renders on refresh().
        gui.setItem(14) { ctx ->
            guiItem {
                material = Material.PAPER
                name = "<white>Hello, ${ctx.player.username}"
                lore(
                    "<gray>Platform: <white>${ctx.platform}",
                    "<gray>Online: <white>${proxy.playerCount}",
                    "<gray>Last click: <white>${ctx.state[lastClick] ?: "none yet"}",
                )
            }
        }

        // 6. Per-viewer state and refresh: each viewer has their own counter.
        gui.setItem(15) { ctx ->
            guiItem {
                material = Material.EMERALD
                amount = ((ctx.state[counter] ?: 0) % 64) + 1
                name = "<green>Counter: ${ctx.state[counter] ?: 0}"
                lore("<gray>Left: +1   Right: -1", "<gray>Shift-left: +10")
                onClick {
                    val state = gui.state(player) ?: return@onClick
                    state.update(counter, 0) {
                        when (event.clickType) {
                            GuiClickType.LEFT -> it + 1
                            GuiClickType.RIGHT -> it - 1
                            GuiClickType.SHIFT_LEFT -> it + 10
                            else -> it
                        }
                    }
                    refresh()
                }
            }
        }

        // 7. Platform-specific variant: no custom model on Bedrock (needs a Geyser custom item mapping).
        gui.setItem(
            16,
            guiItem {
                material = Material.NETHER_STAR
                name = "<gold>Custom model"
                customModelData = 1
                lore("<gray>Uses custom_model_data on Java")
                forPlatform(GuiPlatform.BEDROCK) {
                    customModelData = null
                    lore("<gray>Plain star on Bedrock")
                }
                glow()
            },
        )

        // 8. Clicks other than left/right must be requested explicitly.
        gui.setItem(
            22,
            guiItem {
                material = Material.CLOCK
                name = "<yellow>Click test"
                lore("<gray>Try shift, drop (Q), number keys, F", "<gray>The last click type shows in the paper")
                onClick(
                    GuiClickType.LEFT, GuiClickType.RIGHT, GuiClickType.SHIFT_LEFT, GuiClickType.SHIFT_RIGHT,
                    GuiClickType.MIDDLE, GuiClickType.NUMBER_KEY, GuiClickType.OFFHAND_SWAP,
                    GuiClickType.DROP, GuiClickType.CONTROL_DROP, GuiClickType.DOUBLE_CLICK,
                ) {
                    gui.state(player)?.set(lastClick, event.clickType.name + (event.hotbarSlot?.let { " (hotbar $it)" } ?: ""))
                    refresh()
                }
            },
        )

        gui.fillEmpty(guiItem { material = Material.GRAY_STAINED_GLASS_PANE; name = "<gray> "; hideTooltip = true })

        // Framework-level hooks: logging drags, vetoing clicks, reacting to opens and closes.
        gui.onDrag { it.player.sendMessage(net.kyori.adventure.text.Component.text("Dragged over ${it.guiSlots.size} GUI slots (reverted)")) }
        gui.onClose { event -> proxy.consoleCommandSource.sendMessage(net.kyori.adventure.text.Component.text("${event.player.username} closed the menu: ${event.reason}")) }
        return gui
    }
}
