package nl.klrnbk.minecraft.plugins.gui.api

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiCloseEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiDragEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiOpenEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiRegistration
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import java.util.UUID

/** What a [GuiItemProvider] can see when it renders a slot for one viewer. */
public class GuiRenderContext(
    public val player: Player,
    public val platform: GuiPlatform,
    public val state: GuiState,
    public val gui: Gui,
    public val slot: Int,
)

/** Computes the item of a slot separately for every viewer, each time the GUI renders. Must be fast and side-effect free. */
public fun interface GuiItemProvider {
    public fun provide(context: GuiRenderContext): GuiItem?
}

/**
 * A container GUI shown to players on the proxy.
 *
 * ## Sharing
 * A `Gui` is a *shared* definition: any number of players can look at the same instance at
 * the same time, and changing it updates all of them. What differs per viewer is expressed
 * with [GuiItemProvider]s (dynamic slots) and the per-viewer [GuiState]. A player views at most one
 * `Gui` at a time; opening another replaces the first.
 *
 * ## Compatibility
 * This interface is **implemented only by the framework**; consumers call it, they do not implement it. That is what
 * lets new members be added in minor releases without breaking anyone.
 *
 * ## Threading
 * Every member is thread-safe and can be called from any thread (event handlers, the scheduler, async
 * tasks). Slot changes are atomic: a render always sees a consistent snapshot of the GUI. Methods that
 * change what viewers see (`setItem`, `title`, `clear`, ...) do **not** re-send anything by themselves;
 * call [refresh] (or use [update]) once you are done changing things, which batches the changes into
 * the minimum number of packets.
 */
public interface Gui {
    /** A unique id for logging and bookkeeping. */
    public val id: UUID

    public val layout: GuiLayout

    /** Number of GUI slots. */
    public val size: Int

    /**
     * The title shown above the container. Changing it while players are viewing re-opens the
     * screen for them at the next [refresh] (Minecraft cannot retitle an open container in place).
     */
    public var title: Component

    /** Sets the title from MiniMessage. */
    public fun title(miniMessage: String): Gui

    // ---- items -------------------------------------------------------------------------------

    /** Places [item] in [slot] (`null` empties it). @throws IndexOutOfBoundsException for an invalid slot. */
    public fun setItem(
        slot: Int,
        item: GuiItem?,
    ): Gui

    /** Places a viewer-specific item in [slot]. */
    public fun setItem(
        slot: Int,
        provider: GuiItemProvider,
    ): Gui

    /** Places [item] in every slot of [slots]. */
    public fun setItems(
        slots: Iterable<Int>,
        item: GuiItem,
    ): Gui

    /** The static item in [slot]; `null` for empty slots *and* for dynamic ones (which have no single value). */
    public fun getItem(slot: Int): GuiItem?

    public fun removeItem(slot: Int): Gui

    /** Empties every slot. */
    public fun clear(): Gui

    /** Puts [item] into every slot, overwriting what is there. */
    public fun fill(item: GuiItem): Gui

    /** Puts [item] into every empty slot only. */
    public fun fillEmpty(item: GuiItem): Gui

    /** Applies several changes atomically and refreshes viewers once. */
    public fun update(block: Gui.() -> Unit): Gui

    // ---- viewers -----------------------------------------------------------------------------

    /** Opens the GUI for [player], replacing any GUI they currently view. Fires [GuiOpenEvent] first. */
    public fun open(player: Player): GuiOpenResult

    /** Closes the GUI for [player] if they are viewing it. @return whether they were. */
    public fun close(player: Player): Boolean

    /** Closes the GUI for every viewer. */
    public fun closeAll()

    /** A snapshot of the players currently viewing this GUI. */
    public val viewers: Set<Player>

    public fun isViewedBy(player: Player): Boolean

    /** The player's state, or `null` if they are not viewing this GUI. */
    public fun state(player: Player): GuiState?

    /** Re-renders the GUI for all viewers, sending only what changed. */
    public fun refresh()

    /** Re-renders the GUI for one viewer. */
    public fun refresh(player: Player)

    /** Re-renders only [slot] for all viewers. */
    public fun refreshSlot(slot: Int)

    // ---- listeners ---------------------------------------------------------------------------

    public fun onOpen(handler: (GuiOpenEvent) -> Unit): GuiRegistration

    public fun onClose(handler: (GuiCloseEvent) -> Unit): GuiRegistration

    /** Called for every click in this GUI's screen, before the clicked item's own handlers. */
    public fun onClick(handler: (GuiClickEvent) -> Unit): GuiRegistration

    public fun onDrag(handler: (GuiDragEvent) -> Unit): GuiRegistration
}
