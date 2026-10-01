package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.protocol.ConnectionState
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import com.velocitypowered.api.proxy.Player
import io.mockk.mockk
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import nl.klrnbk.minecraft.plugins.gui.common.protocol.InboundHandler
import nl.klrnbk.minecraft.plugins.gui.common.protocol.PacketVerdict
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.WindowSpec
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.util.Optional
import java.util.UUID

/** Tests the protocol adapter against a recording transport: which packets does each GUI operation produce? */
class PacketEventsGuiProtocolTest {
    init {
        PeTestSupport.install()
    }

    private class FakeTransport : PacketTransport {
        var info: ConnectionInfo? = ConnectionInfo(ClientVersion.V_26_2, ConnectionState.PLAY)
        val sent = mutableListOf<PacketWrapper<*>>()

        override fun connection(player: Player) = info

        override fun send(
            player: Player,
            packet: PacketWrapper<*>,
        ) {
            sent += packet
        }
    }

    private class FakeRegistrar : PacketEventsGuiProtocol.ListenerRegistrar {
        var registered: PacketListenerCommon? = null
        var unregistered = 0

        override fun register(listener: PacketListenerCommon): PacketListenerCommon {
            registered = listener
            return listener
        }

        override fun unregister(listener: PacketListenerCommon) {
            unregistered++
        }
    }

    private class RecordingHandler(
        var clickVerdict: PacketVerdict = PacketVerdict.CONSUME,
        var closeVerdict: PacketVerdict = PacketVerdict.CONSUME,
    ) : InboundHandler {
        val clicks = mutableListOf<Pair<UUID, RawClick>>()
        val closes = mutableListOf<Pair<UUID, Int>>()
        val backendOpens = mutableListOf<UUID>()

        override fun onClick(
            player: UUID,
            click: RawClick,
        ): PacketVerdict = clickVerdict.also { clicks += player to click }

        override fun onClose(
            player: UUID,
            containerId: Int,
        ): PacketVerdict = closeVerdict.also { closes += player to containerId }

        override fun onBackendContainerOpened(player: UUID) {
            backendOpens += player
        }
    }

    private val transport = FakeTransport()
    private val mirror = PlayerInventoryMirror()
    private val registrar = FakeRegistrar()
    private val protocol = PacketEventsGuiProtocol(transport, PeItemConverter(NOPLogger.NOP_LOGGER), mirror, NOPLogger.NOP_LOGGER, registrar)
    private val player: Player = testPlayer("Alex")

    private fun stone(n: Int) = ItemStack.builder().type(ItemTypes.STONE).amount(n).build()

    private fun window(
        layout: GuiLayout = GuiLayout.chest(3),
        items: (Int) -> nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem? = { null },
    ) = WindowSpec(103, 42, layout, Component.text("Menu"), List(layout.size, items))

    // ---- check ---------------------------------------------------------------------------------

    @Test
    fun `check reports why a window cannot be opened`() {
        assertEquals(ProtocolCheck.OK, protocol.check(player))

        transport.info = null
        assertEquals(ProtocolCheck.PLAYER_DISCONNECTED, protocol.check(player))

        transport.info = ConnectionInfo(ClientVersion.V_26_2, ConnectionState.CONFIGURATION)
        assertEquals(ProtocolCheck.NOT_IN_PLAY_PHASE, protocol.check(player))
        transport.info = ConnectionInfo(ClientVersion.V_26_2, ConnectionState.LOGIN)
        assertEquals(ProtocolCheck.NOT_IN_PLAY_PHASE, protocol.check(player))

        transport.info = ConnectionInfo(ClientVersion.V_1_21_4, ConnectionState.PLAY)
        assertEquals(ProtocolCheck.UNSUPPORTED_CLIENT, protocol.check(player))

        io.mockk.every { player.isActive } returns false
        assertEquals(ProtocolCheck.PLAYER_DISCONNECTED, protocol.check(player))
    }

    // ---- outbound ------------------------------------------------------------------------------

    @Test
    fun `open sends open screen then the full content`() {
        protocol.open(player, window(GuiLayout.chest(3)) { if (it == 4) guiItem { material = Material.DIAMOND } else null })

        val (open, content) = transport.sent
        open as WrapperPlayServerOpenWindow
        content as WrapperPlayServerWindowItems
        assertEquals(103, open.containerId)
        assertEquals(2, open.type)
        assertEquals(Component.text("Menu"), open.title)
        assertEquals(103, content.windowId)
        assertEquals(42, content.stateId)
        assertEquals(27 + 36, content.items.size)
        assertEquals(ItemTypes.DIAMOND, content.items[4].type)
        assertTrue(content.items[5].isEmpty)
        assertTrue(content.carriedItem.get().isEmpty, "cursor is emptied")
    }

    @Test
    fun `the player's own inventory from the backend is shown below the gui`() {
        mirror.replaceAll(player.uniqueId, List(46) { if (it in 9..44) stone(it) else ItemStack.EMPTY })
        protocol.open(player, window(GuiLayout.Hopper))
        val content = transport.sent[1] as WrapperPlayServerWindowItems
        assertEquals(5 + 36, content.items.size)
        assertEquals(9, content.items[5].amount, "player slot 0 follows gui slot 4")
        assertEquals(44, content.items.last().amount)
    }

    @Test
    fun `resync sends only content`() {
        protocol.resync(player, window())
        assertEquals(1, transport.sent.size)
        assertTrue(transport.sent.single() is WrapperPlayServerWindowItems)
    }

    @Test
    fun `slot updates send one set-slot per slot and empty stacks for null`() {
        protocol.updateSlots(player, 103, 50, mapOf(2 to guiItem { material = Material.EMERALD; amount = 9 }, 3 to null))
        val (first, second) = transport.sent.map { it as WrapperPlayServerSetSlot }
        assertEquals(listOf(103, 103), listOf(first.windowId, second.windowId))
        assertEquals(listOf(50, 50), listOf(first.stateId, second.stateId))
        assertEquals(2, first.slot)
        assertEquals(ItemTypes.EMERALD, first.item.type)
        assertEquals(9, first.item.amount)
        assertEquals(3, second.slot)
        assertTrue(second.item.isEmpty)
    }

    @Test
    fun `close sends a close-window packet with the container id`() {
        protocol.close(player, 103)
        assertEquals(103, (transport.sent.single() as WrapperPlayServerCloseWindow).windowId)
    }

    @Test
    fun `items are converted for the client version of the receiving player`() {
        transport.info = ConnectionInfo(ClientVersion.V_26_3, ConnectionState.PLAY)
        protocol.open(player, window { guiItem { material = Material.STONE } })
        assertEquals(2, transport.sent.size)
    }

    @Test
    fun `forget clears the inventory mirror`() {
        mirror.set(player.uniqueId, 9, stone(1))
        protocol.forget(player.uniqueId)
        assertFalse(mirror.known(player.uniqueId))
    }

    // ---- inbound -------------------------------------------------------------------------------

    @Test
    fun `binding registers a packet listener and unbinding removes it`() {
        protocol.bind(RecordingHandler())
        assertNotNull(registrar.registered)
        protocol.unbind()
        assertEquals(1, registrar.unregistered)
        // after unbind nothing reaches a handler and nothing is swallowed
        assertFalse(protocol.handleClick(player.uniqueId, WrapperPlayClientClickWindow(103, 1, 0, 0, com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType.PICKUP, emptyMap(), Optional.empty())))
    }

    @Test
    fun `clicks are decoded and swallowed when the handler consumes them`() {
        val handler = RecordingHandler()
        protocol.bind(handler)
        val packet =
            WrapperPlayClientClickWindow(
                103, 7, 13, 1,
                WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE,
                emptyMap(),
                Optional.empty(),
            )
        assertTrue(protocol.handleClick(player.uniqueId, packet))
        val (uuid, click) = handler.clicks.single()
        assertEquals(player.uniqueId, uuid)
        assertEquals(103, click.containerId)
        assertEquals(13, click.slot)
        assertEquals(1, click.button)
        assertEquals(nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode.QUICK_MOVE, click.mode)
        assertNull(click.cursor)
    }

    @Test
    fun `clicks the handler passes on are not swallowed`() {
        protocol.bind(RecordingHandler(clickVerdict = PacketVerdict.PASS, closeVerdict = PacketVerdict.PASS))
        val click = WrapperPlayClientClickWindow(2, 1, 0, 0, WrapperPlayClientClickWindow.WindowClickType.PICKUP, emptyMap(), Optional.empty())
        assertFalse(protocol.handleClick(player.uniqueId, click))
        assertFalse(protocol.handleClose(player.uniqueId, WrapperPlayClientCloseWindow(2)))
    }

    @Test
    fun `close packets are forwarded with their window id`() {
        val handler = RecordingHandler()
        protocol.bind(handler)
        assertTrue(protocol.handleClose(player.uniqueId, WrapperPlayClientCloseWindow(103)))
        assertEquals(103, handler.closes.single().second)
    }

    @Test
    fun `a backend open-screen is reported`() {
        val handler = RecordingHandler()
        protocol.bind(handler)
        protocol.handleBackendOpen(player.uniqueId)
        assertEquals(listOf(player.uniqueId), handler.backendOpens)
    }

    @Test
    fun `backend inventory packets feed the mirror`() {
        val id = player.uniqueId
        protocol.handleWindowItems(id, WrapperPlayServerWindowItems(0, 1, List(46) { if (it == 20) stone(3) else ItemStack.EMPTY }, ItemStack.EMPTY))
        assertEquals(3, mirror.playerSlots(id)[11].amount)

        protocol.handleSetSlot(id, WrapperPlayServerSetSlot(0, 2, 36, stone(8)))
        assertEquals(8, mirror.playerSlots(id)[27].amount)

        protocol.handleSetSlot(id, WrapperPlayServerSetSlot(-2, 3, 0, stone(6))) // legacy: hotbar 0
        assertEquals(6, mirror.playerSlots(id)[27].amount)

        // other windows are none of the mirror's business
        protocol.handleWindowItems(id, WrapperPlayServerWindowItems(5, 1, List(63) { stone(1) }, ItemStack.EMPTY))
        protocol.handleSetSlot(id, WrapperPlayServerSetSlot(5, 2, 36, stone(1)))
        assertEquals(6, mirror.playerSlots(id)[27].amount)
        assertEquals(3, mirror.playerSlots(id)[11].amount)
    }

    @Test
    fun `the description names the protocol layer`() {
        assertTrue("PacketEvents" in protocol.description)
        assertTrue("26.x" in protocol.description)
    }
}
