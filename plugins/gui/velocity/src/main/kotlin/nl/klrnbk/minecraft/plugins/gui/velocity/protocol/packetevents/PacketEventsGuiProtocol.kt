package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.ConnectionState
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.common.protocol.GuiProtocol
import nl.klrnbk.minecraft.plugins.gui.common.protocol.InboundHandler
import nl.klrnbk.minecraft.plugins.gui.common.protocol.PacketVerdict
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck
import nl.klrnbk.minecraft.plugins.gui.common.protocol.WindowSpec
import org.slf4j.Logger
import java.util.UUID

/**
 * The Minecraft 26.x implementation of [GuiProtocol], built on PacketEvents.
 *
 * ### The protocol flow it implements
 * 1. **Open** — `ClientboundOpenScreen(containerId, menuType, title)` followed by `ClientboundContainerSetContent
 *    (containerId, stateId, slots, cursor)`. The window id is one from the reserved 101..127 range, so it can never
 *    collide with a real backend window.
 * 2. **Click** — the client sends `ServerboundContainerClick`. The proxy *consumes* it (the backend has never heard of
 *    this window) and answers with a fresh `SetContent` that empties the cursor, which is how a client that
 *    optimistically moved an item is snapped back.
 * 3. **Update** — `ClientboundContainerSetSlot` for a few slots, `SetContent` for many.
 * 4. **Close** — client-initiated: `ServerboundContainerClose`, consumed. Proxy-initiated: `ClientboundContainerClose`.
 *
 * Everything version-specific is confined to this package: menu ids ([MenuTypes]), item encoding
 * ([PeItemConverter]), click decoding ([PeClickDecoder]) and the packet wrappers themselves.
 */
internal class PacketEventsGuiProtocol(
    private val transport: PacketTransport,
    private val items: PeItemConverter,
    private val mirror: PlayerInventoryMirror,
    private val logger: Logger,
    private val registrar: ListenerRegistrar = PacketEventsRegistrar,
) : GuiProtocol {
    /** Registers/unregisters our packet listener; a seam for tests. */
    interface ListenerRegistrar {
        fun register(listener: PacketListenerCommon): PacketListenerCommon

        fun unregister(listener: PacketListenerCommon)
    }

    object PacketEventsRegistrar : ListenerRegistrar {
        override fun register(listener: PacketListenerCommon): PacketListenerCommon = PacketEvents.getAPI().eventManager.registerListener(listener)

        override fun unregister(listener: PacketListenerCommon) {
            PacketEvents.getAPI().eventManager.unregisterListener(listener)
        }
    }

    override val description: String
        get() = "PacketEvents ${PacketEvents.getAPI()?.version ?: "?"} (Minecraft 26.x menu table, clients >= ${MenuTypes.MINIMUM_CLIENT.name})"

    @Volatile
    private var handler: InboundHandler? = null
    private var registered: PacketListenerCommon? = null

    override fun bind(handler: InboundHandler) {
        this.handler = handler
        registered = registrar.register(Listener())
    }

    override fun unbind() {
        registered?.let(registrar::unregister)
        registered = null
        handler = null
    }

    // ---- outbound ------------------------------------------------------------------------------

    override fun check(player: Player): ProtocolCheck {
        if (!player.isActive) return ProtocolCheck.PLAYER_DISCONNECTED
        val connection = transport.connection(player) ?: return ProtocolCheck.PLAYER_DISCONNECTED
        if (connection.state != ConnectionState.PLAY) return ProtocolCheck.NOT_IN_PLAY_PHASE
        if (!MenuTypes.isSupported(connection.clientVersion)) return ProtocolCheck.UNSUPPORTED_CLIENT
        return ProtocolCheck.OK
    }

    override fun open(
        player: Player,
        window: WindowSpec,
    ) {
        transport.send(player, PacketFactory.openWindow(window))
        transport.send(player, content(player, window))
    }

    override fun resync(
        player: Player,
        window: WindowSpec,
    ) {
        transport.send(player, content(player, window))
    }

    override fun updateSlots(
        player: Player,
        containerId: Int,
        stateId: Int,
        changes: Map<Int, GuiItem?>,
    ) {
        val version = transport.connection(player)?.clientVersion ?: return
        for ((slot, item) in changes) {
            val stack = item?.let { items.convert(it, version) } ?: ItemStack.EMPTY
            transport.send(player, PacketFactory.setSlot(containerId, stateId, slot, stack))
        }
    }

    override fun close(
        player: Player,
        containerId: Int,
    ) {
        transport.send(player, PacketFactory.closeWindow(containerId))
    }

    override fun forget(player: UUID) {
        mirror.forget(player)
    }

    private fun content(
        player: Player,
        window: WindowSpec,
    ): WrapperPlayServerWindowItems {
        val version = requireNotNull(transport.connection(player)) { "connection of ${player.username} vanished" }.clientVersion
        val guiStacks = window.items.map { it?.let { item -> items.convert(item, version) } ?: ItemStack.EMPTY }
        return PacketFactory.windowContent(window.containerId, window.stateId, guiStacks, mirror.playerSlots(player.uniqueId))
    }

    // ---- inbound -------------------------------------------------------------------------------

    /** @return `true` if the packet must be swallowed instead of reaching the backend. */
    internal fun handleClick(
        player: UUID,
        packet: WrapperPlayClientClickWindow,
    ): Boolean = handler?.onClick(player, PeClickDecoder.decode(packet)) == PacketVerdict.CONSUME

    /** @return `true` if the packet must be swallowed instead of reaching the backend. */
    internal fun handleClose(
        player: UUID,
        packet: WrapperPlayClientCloseWindow,
    ): Boolean = handler?.onClose(player, packet.windowId) == PacketVerdict.CONSUME

    internal fun handleBackendOpen(player: UUID) {
        handler?.onBackendContainerOpened(player)
    }

    internal fun handleWindowItems(
        player: UUID,
        packet: WrapperPlayServerWindowItems,
    ) {
        if (packet.windowId == PLAYER_INVENTORY_WINDOW) mirror.replaceAll(player, packet.items)
    }

    internal fun handleSetSlot(
        player: UUID,
        packet: WrapperPlayServerSetSlot,
    ) {
        when (packet.windowId) {
            PLAYER_INVENTORY_WINDOW -> mirror.set(player, packet.slot, packet.item)
            LEGACY_INVENTORY_SLOT_WINDOW ->
                mirror.set(player, PlayerInventoryMirror.containerZeroSlotOfInventoryIndex(packet.slot), packet.item)
        }
    }

    /** The PacketEvents hook: decode, delegate, apply the verdict. No logic of its own. */
    private inner class Listener : PacketListenerAbstract(PacketListenerPriority.HIGH) {
        override fun onPacketReceive(event: PacketReceiveEvent) {
            when (event.packetType) {
                PacketType.Play.Client.CLICK_WINDOW -> {
                    val uuid = event.user.uuid ?: return
                    if (handleClick(uuid, WrapperPlayClientClickWindow(event))) event.isCancelled = true
                }

                PacketType.Play.Client.CLOSE_WINDOW -> {
                    val uuid = event.user.uuid ?: return
                    if (handleClose(uuid, WrapperPlayClientCloseWindow(event))) event.isCancelled = true
                }

                else -> {}
            }
        }

        override fun onPacketSend(event: PacketSendEvent) {
            val uuid = event.user.uuid ?: return
            when (event.packetType) {
                PacketType.Play.Server.OPEN_WINDOW, PacketType.Play.Server.OPEN_HORSE_WINDOW -> handleBackendOpen(uuid)
                PacketType.Play.Server.WINDOW_ITEMS -> handleWindowItems(uuid, WrapperPlayServerWindowItems(event))
                PacketType.Play.Server.SET_SLOT -> handleSetSlot(uuid, WrapperPlayServerSetSlot(event))
                else -> {}
            }
        }
    }

    private companion object {
        const val PLAYER_INVENTORY_WINDOW = 0

        /** Pre-1.21.2 servers addressed player-inventory slots with window id -2. */
        const val LEGACY_INVENTORY_SLOT_WINDOW = -2
    }
}

/** Builds the clientbound container packets. Pure functions so protocol tests can assert on the result. */
internal object PacketFactory {
    fun openWindow(window: WindowSpec): WrapperPlayServerOpenWindow =
        WrapperPlayServerOpenWindow(window.containerId, MenuTypes.idFor(window.layout), window.title)

    fun windowContent(
        containerId: Int,
        stateId: Int,
        guiStacks: List<ItemStack>,
        playerStacks: List<ItemStack>,
    ): WrapperPlayServerWindowItems = WrapperPlayServerWindowItems(containerId, stateId, guiStacks + playerStacks, ItemStack.EMPTY)

    fun setSlot(
        containerId: Int,
        stateId: Int,
        slot: Int,
        stack: ItemStack,
    ): WrapperPlayServerSetSlot = WrapperPlayServerSetSlot(containerId, stateId, slot, stack)

    fun closeWindow(containerId: Int): WrapperPlayServerCloseWindow = WrapperPlayServerCloseWindow(containerId)
}

