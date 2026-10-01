package nl.klrnbk.minecraft.plugins.gui.api.event

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.InternalGuiApi
import nl.klrnbk.minecraft.plugins.gui.api.SlotArea
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.api.item.Material

/** Base type of everything the framework reports. */
public sealed interface GuiEvent {
    public val player: Player
    public val gui: Gui
    public val platform: GuiPlatform
}

/** An event whose default effect can be suppressed. */
public sealed interface CancellableGuiEvent : GuiEvent {
    public var cancelled: Boolean
}

/**
 * A GUI is about to open for [player]. Cancelling prevents it from opening.
 *
 * Handlers run synchronously on the thread calling `Gui.open`, before any packet is sent.
 */
public class GuiOpenEvent
    @InternalGuiApi
    constructor(
        override val player: Player,
        override val gui: Gui,
        override val platform: GuiPlatform,
    ) : CancellableGuiEvent {
        override var cancelled: Boolean = false
    }

/**
 * A GUI closed for [player]. Not cancellable: by the time it fires the screen is already gone
 * (or the player is). Handlers run on the viewer's event thread — see [GuiClickEvent].
 */
public class GuiCloseEvent
    @InternalGuiApi
    constructor(
        override val player: Player,
        override val gui: Gui,
        override val platform: GuiPlatform,
        public val reason: GuiCloseReason,
    ) : GuiEvent

/**
 * A count-and-type snapshot of the item on the player's cursor.
 *
 * Since Minecraft 1.21.5 the client only sends a *hash* of the cursor item's components, so
 * type and amount are all a proxy can know. It is `null` on [GuiClickEvent.cursorItem]
 * when the cursor is empty.
 */
public class CursorItem(
    public val material: Material,
    public val amount: Int,
) {
    override fun equals(other: Any?): Boolean = other is CursorItem && other.material == material && other.amount == amount

    override fun hashCode(): Int = 31 * material.hashCode() + amount

    override fun toString(): String = "CursorItem($material x$amount)"
}

/**
 * A player clicked inside the open GUI screen (GUI slots *and* their own inventory).
 *
 * **Inventory safety.** The GUI is a virtual container that the proxy owns, so items can
 * never really move: whatever the client predicted is always reverted with a fresh
 * synchronisation, no matter what a handler does. [cancelled] therefore controls the
 * *action*: when a handler sets it, the clicked item's own click handlers do not run.
 *
 * **Threading.** Handlers run on a per-player serial executor (never on a netty event loop):
 * events for one player are delivered one at a time, in the order the client sent them;
 * different players run in parallel. Handlers may block briefly but should not block for long.
 */
public class GuiClickEvent
    @InternalGuiApi
    constructor(
        override val player: Player,
        override val gui: Gui,
        override val platform: GuiPlatform,
        /** The clicked slot: `0 until gui.size` for [SlotArea.GUI], the raw container slot otherwise, `-999` outside. */
        public val slot: Int,
        public val area: SlotArea,
        /** The item shown in the clicked GUI slot (platform overrides applied); `null` for empty slots and other areas. */
        public val item: GuiItem?,
        public val clickType: GuiClickType,
        /** The hotbar slot (0..8) of a [GuiClickType.NUMBER_KEY] click, otherwise `null`. */
        public val hotbarSlot: Int?,
        public val cursorItem: CursorItem?,
    ) : CancellableGuiEvent {
        override var cancelled: Boolean = false

        /** True when the click landed on a slot of the GUI itself. */
        public val isInGui: Boolean get() = area == SlotArea.GUI
    }

/**
 * A completed drag ("paint") across several slots. Delivered once, when the player releases the button.
 *
 * [guiSlots] are the dragged-over slots that belong to the GUI; [inventorySlots] are the ones of the
 * player's inventory, numbered 0..35 in screen order (27 main slots, then the 9 hotbar slots). Like every
 * click, the drag is always reverted.
 */
public class GuiDragEvent
    @InternalGuiApi
    constructor(
        override val player: Player,
        override val gui: Gui,
        override val platform: GuiPlatform,
        public val clickType: GuiClickType,
        public val guiSlots: Set<Int>,
        public val inventorySlots: Set<Int>,
        public val cursorItem: CursorItem?,
    ) : CancellableGuiEvent {
        override var cancelled: Boolean = false
    }

/** Something that can be undone: returned by every listener registration. */
public fun interface GuiRegistration : AutoCloseable {
    /** Removes the listener. Idempotent. */
    override fun close()
}

/** The framework-wide event bus. Obtain it from `GuiApi.events`. */
public interface GuiEvents {
    /**
     * Registers [handler] for events of [type] (including subtypes, so `GuiEvent::class.java` sees everything).
     * Global handlers run *before* per-item handlers, in registration order.
     */
    public fun <T : GuiEvent> subscribe(
        type: Class<T>,
        handler: (T) -> Unit,
    ): GuiRegistration
}

/** Kotlin-friendly `events.on<GuiClickEvent> { ... }`. */
public inline fun <reified T : GuiEvent> GuiEvents.on(noinline handler: (T) -> Unit): GuiRegistration = subscribe(T::class.java, handler)
