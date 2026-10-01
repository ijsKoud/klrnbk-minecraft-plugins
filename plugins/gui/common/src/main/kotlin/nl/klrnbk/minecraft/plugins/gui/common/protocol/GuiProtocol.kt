package nl.klrnbk.minecraft.plugins.gui.common.protocol

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.event.CursorItem
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import java.util.UUID

/**
 * The boundary between the version-independent GUI logic and the Minecraft wire protocol.
 *
 * Everything above this interface (sessions, events, actions, the public API) knows nothing
 * about packets, protocol numbers or registry ids. Everything below it (currently
 * `PacketEventsGuiProtocol`) knows nothing about sessions or events. A Minecraft update that
 * changes the container packets is absorbed by changing or replacing the implementation of
 * this interface — see IMPLEMENTATION.md, "Upgrading to a new Minecraft version".
 *
 * Implementations must be thread-safe and must not block: they are invoked from netty event
 * loops (inbound) and arbitrary plugin threads (outbound).
 */
interface GuiProtocol {
    /** For logs and `GuiApi.protocolDescription`. */
    val description: String

    /** Whether a GUI can be opened for [player] right now. */
    fun check(player: Player): ProtocolCheck

    /** Opens (or re-opens, when the container id is already showing) the window with its full content. */
    fun open(
        player: Player,
        window: WindowSpec,
    )

    /** Re-sends the full content of an open window and empties the cursor, undoing any client-side prediction. */
    fun resync(
        player: Player,
        window: WindowSpec,
    )

    /** Updates individual slots of an open window. `null` empties a slot. */
    fun updateSlots(
        player: Player,
        containerId: Int,
        stateId: Int,
        changes: Map<Int, GuiItem?>,
    )

    /** Tells the client to close the window with [containerId]. */
    fun close(
        player: Player,
        containerId: Int,
    )

    /** Drops everything remembered about a player (called on disconnect). */
    fun forget(player: UUID)

    /** Starts delivering inbound container packets to [handler]. */
    fun bind(handler: InboundHandler)

    /** Stops delivering inbound packets and unregisters from the underlying library. */
    fun unbind()
}

enum class ProtocolCheck {
    OK,
    PLAYER_DISCONNECTED,
    NOT_IN_PLAY_PHASE,
    UNSUPPORTED_CLIENT,
}

/** Everything needed to render one window. [items] always has exactly `layout.size` entries. */
class WindowSpec(
    val containerId: Int,
    val stateId: Int,
    val layout: GuiLayout,
    val title: Component,
    val items: List<GuiItem?>,
) {
    init {
        require(items.size == layout.size) { "expected ${layout.size} items, got ${items.size}" }
    }
}

/** The vanilla `ClickType` of a container click packet, independent of how a protocol version encodes it. */
enum class RawClickMode {
    PICKUP,
    QUICK_MOVE,
    SWAP,
    CLONE,
    THROW,
    QUICK_CRAFT,
    PICKUP_ALL,
    UNKNOWN,
}

/** A container click, already decoded from the wire format. */
class RawClick(
    val containerId: Int,
    val stateId: Int,
    val slot: Int,
    val button: Int,
    val mode: RawClickMode,
    val cursor: CursorItem?,
)

/** What the protocol layer should do with an inbound packet after the GUI logic looked at it. */
enum class PacketVerdict {
    /** Let the packet continue to the backend server. */
    PASS,

    /** Swallow it: it belongs to a proxy-owned window and the backend must never see it. */
    CONSUME,
}

/** Receives decoded inbound container traffic. Implemented by the GUI manager. */
interface InboundHandler {
    fun onClick(
        player: UUID,
        click: RawClick,
    ): PacketVerdict

    fun onClose(
        player: UUID,
        containerId: Int,
    ): PacketVerdict

    /** The backend server sent an "open screen" packet, replacing whatever the client shows. */
    fun onBackendContainerOpened(player: UUID)
}

/**
 * Container ids the framework hands out for proxy-owned windows. Vanilla servers count ids from 1 to
 * 100, so 101..127 can never collide with a real backend window, and 127 is the highest value that
 * still fits the signed byte older protocol versions use.
 */
object ContainerIds {
    const val FIRST = 101
    const val LAST = 127

    fun isReserved(id: Int): Boolean = id in FIRST..LAST

    /** The id to use after [previous] (or the first one), cycling through the range. */
    fun next(previous: Int?): Int = if (previous == null || !isReserved(previous) || previous == LAST) FIRST else previous + 1
}
