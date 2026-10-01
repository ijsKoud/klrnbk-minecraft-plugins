package nl.klrnbk.minecraft.plugins.gui.api

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiEvents

/**
 * Entry point of the KLRNBK GUI framework. Obtain the instance from [GuiProvider] (after the
 * proxy has initialised) — never construct one.
 *
 * Nothing in this API exposes Minecraft protocol types, so a protocol upgrade inside the
 * `klrnbk-gui` plugin does not require recompiling or changing consumers.
 *
 * All members are thread-safe. Like [Gui], this interface is implemented only by the framework.
 */
public interface GuiApi {
    /** Creates a chest GUI with [rows] rows (1..6). [title] is MiniMessage. */
    public fun create(
        title: String,
        rows: Int,
    ): Gui

    public fun create(
        title: Component,
        rows: Int,
    ): Gui

    public fun create(
        title: String,
        layout: GuiLayout,
    ): Gui

    public fun create(
        title: Component,
        layout: GuiLayout,
    ): Gui

    /** The platform [player] plays on (cached for the length of their connection). */
    public fun platformOf(player: Player): GuiPlatform

    /** The GUI [player] is currently looking at, if any. */
    public fun currentGui(player: Player): Gui?

    /** Closes whatever GUI [player] is viewing. @return whether there was one. */
    public fun closeGui(player: Player): Boolean

    /** GUIs defined in YAML files in the plugin's `menus/` folder. */
    public val menus: GuiMenus

    /** The event bus for framework-wide listeners. */
    public val events: GuiEvents

    /** A human-readable name of the protocol implementation in use, e.g. `packetevents 2.14.0 (MC 26.x)`. For diagnostics. */
    public val protocolDescription: String
}

/** `guiApi.gui("<gold>Menu", rows = 3) { setItem(13, ...) }` — creates and configures in one go. */
public fun GuiApi.gui(
    title: String,
    rows: Int = 3,
    configure: Gui.() -> Unit,
): Gui = create(title, rows).apply(configure)
