package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.SlotArea
import nl.klrnbk.minecraft.plugins.gui.api.StateKey
import nl.klrnbk.minecraft.plugins.gui.api.gui
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.event.CursorItem
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiCloseEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiDragEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiOpenEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.on
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import nl.klrnbk.minecraft.plugins.gui.common.FakeGuiProtocol.Op
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ContainerIds
import nl.klrnbk.minecraft.plugins.gui.common.protocol.PacketVerdict
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GuiManagerTest {
    private val h = GuiTestHarness()
    private val alex = testPlayer("Alex")
    private val bea = testPlayer("Bea")

    private fun Gui.containerIdFor(): Int = h.protocol.opsOf<Op.Open>().last().window.containerId

    // ---- opening -------------------------------------------------------------------------------

    @Test
    fun `opening sends the window with the gui's items and a reserved container id`() {
        val item = guiItem { material = Material.DIAMOND }
        val gui = h.api.create("<gold>Menu", 3).setItem(13, item)
        assertEquals(GuiOpenResult.OPENED, gui.open(alex))

        val open = h.protocol.opsOf<Op.Open>().single()
        assertSame(alex, open.player)
        assertEquals(27, open.window.items.size)
        assertSame(item, open.window.items[13])
        assertTrue(ContainerIds.isReserved(open.window.containerId))
        assertEquals(gui.layout, open.window.layout)
        assertTrue(gui.isViewedBy(alex))
        assertSame(gui, h.api.currentGui(alex))
    }

    @Test
    fun `opening reports why it failed`() {
        val gui = h.api.create("x", 1)
        h.protocol.checkResult = ProtocolCheck.UNSUPPORTED_CLIENT
        assertEquals(GuiOpenResult.UNSUPPORTED_CLIENT, gui.open(alex))
        h.protocol.checkResult = ProtocolCheck.NOT_IN_PLAY_PHASE
        assertEquals(GuiOpenResult.NOT_IN_PLAY_PHASE, gui.open(alex))
        h.protocol.checkResult = ProtocolCheck.PLAYER_DISCONNECTED
        assertEquals(GuiOpenResult.PLAYER_DISCONNECTED, gui.open(alex))
        h.protocol.checkResult = ProtocolCheck.OK
        io.mockk.every { alex.isActive } returns false
        assertEquals(GuiOpenResult.PLAYER_DISCONNECTED, gui.open(alex))
        assertTrue(h.protocol.ops.isEmpty())
        assertFalse(gui.isViewedBy(alex))
    }

    @Test
    fun `a protocol failure leaves no session behind`() {
        val gui = h.api.create("x", 1)
        h.protocol.failOpen = true
        assertEquals(GuiOpenResult.PROTOCOL_ERROR, gui.open(alex))
        assertFalse(gui.isViewedBy(alex))
        assertEquals(0, h.manager.sessionCount)
    }

    @Test
    fun `a failed re-open keeps the previous gui open`() {
        val first = h.api.create("a", 1)
        val second = h.api.create("b", 1)
        first.open(alex)
        h.protocol.failOpen = true
        assertEquals(GuiOpenResult.PROTOCOL_ERROR, second.open(alex))
        assertTrue(first.isViewedBy(alex))
    }

    @Test
    fun `GuiOpenEvent can cancel the opening`() {
        val gui = h.api.create("x", 1)
        val seen = mutableListOf<GuiOpenEvent>()
        h.api.events.on<GuiOpenEvent> {
            seen += it
            it.cancelled = true
        }
        assertEquals(GuiOpenResult.CANCELLED, gui.open(alex))
        assertTrue(h.protocol.ops.isEmpty())
        assertFalse(gui.isViewedBy(alex))
        assertSame(gui, seen.single().gui)
        assertEquals(GuiPlatform.JAVA, seen.single().platform)
    }

    @Test
    fun `per-gui open handlers see the event too`() {
        val gui = h.api.create("x", 1)
        var called = false
        gui.onOpen { called = true; it.cancelled = true }
        assertEquals(GuiOpenResult.CANCELLED, gui.open(alex))
        assertTrue(called)
    }

    @Test
    fun `opening another gui replaces the first and cycles the container id`() {
        val first = h.api.create("a", 1)
        val second = h.api.create("b", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        h.api.events.on<GuiCloseEvent> { closes += it }

        first.open(alex)
        val firstId = first.containerIdFor()
        second.open(alex)
        val secondId = second.containerIdFor()

        assertNotEquals(firstId, secondId)
        assertFalse(first.isViewedBy(alex))
        assertTrue(second.isViewedBy(alex))
        assertEquals(1, h.manager.sessionCount)
        assertEquals(GuiCloseReason.REPLACED, closes.single().reason)
        assertSame(first, closes.single().gui)
        // The client replaces its own screen; no explicit close packet is needed (and would close the new one).
        assertTrue(h.protocol.opsOf<Op.Close>().isEmpty())
    }

    @Test
    fun `container ids stay inside the reserved range when cycling`() {
        val gui = h.api.create("x", 1)
        repeat(100) { gui.open(alex) }
        h.protocol.opsOf<Op.Open>().forEach { assertTrue(ContainerIds.isReserved(it.window.containerId)) }
        assertEquals(ContainerIds.FIRST, ContainerIds.next(null))
        assertEquals(ContainerIds.FIRST, ContainerIds.next(ContainerIds.LAST), "wraps around")
        assertEquals(ContainerIds.FIRST, ContainerIds.next(3), "a foreign id restarts the cycle")
        assertEquals(ContainerIds.FIRST + 1, ContainerIds.next(ContainerIds.FIRST))
        assertEquals(ContainerIds.LAST - ContainerIds.FIRST + 1, h.protocol.opsOf<Op.Open>().map { it.window.containerId }.distinct().size)
    }

    @Test
    fun `the same gui can be shown to several players at once`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        gui.open(bea)
        assertEquals(setOf(alex, bea), gui.viewers)
        assertEquals(2, h.manager.sessionCount)
        gui.close(alex)
        assertEquals(setOf(bea), gui.viewers)
    }

    // ---- per-player state ----------------------------------------------------------------------

    @Test
    fun `state is per viewer and discarded with the session`() {
        val page = StateKey<Int>("page")
        val gui = h.api.create("x", 1)
        gui.open(alex)
        gui.open(bea)
        gui.state(alex)!![page] = 3
        assertEquals(3, gui.state(alex)!![page])
        assertNull(gui.state(bea)!![page])
        gui.close(alex)
        assertNull(gui.state(alex))
        gui.open(alex)
        assertNull(gui.state(alex)!![page])
    }

    @Test
    fun `dynamic slots can render differently per viewer from state`() {
        val page = StateKey<Int>("page")
        val gui = h.api.create("x", 1)
        gui.setItem(0) { ctx -> guiItem { material = Material.PAPER; amount = ctx.state[page] ?: 1 } }
        gui.open(alex)
        gui.open(bea)
        gui.state(bea)!![page] = 5
        gui.refresh()

        val update = h.protocol.opsOf<Op.Update>().single()
        assertSame(bea, update.player)
        assertEquals(5, update.changes[0]!!.amount)
        assertEquals(1, h.protocol.opsOf<Op.Open>().first { it.player === alex }.window.items[0]!!.amount)
    }

    // ---- refreshing ----------------------------------------------------------------------------

    @Test
    fun `refresh sends only the slots that changed`() {
        val gui = h.api.create("x", 3).setItem(0, guiItem { material = Material.STONE })
        gui.open(alex)
        h.protocol.ops.clear()

        gui.refresh()
        assertTrue(h.protocol.ops.isEmpty(), "nothing changed, nothing sent")

        gui.setItem(0, guiItem { material = Material.STONE }) // identical look
        gui.refresh()
        assertTrue(h.protocol.ops.isEmpty(), "a visually identical replacement is not re-sent")

        gui.setItem(0, guiItem { material = Material.DIRT_OR("dirt") })
        gui.setItem(5, guiItem { material = Material.DIAMOND })
        gui.refresh()
        val update = h.protocol.opsOf<Op.Update>().single()
        assertEquals(setOf(0, 5), update.changes.keys)
    }

    @Test
    fun `removing an item sends an empty slot`() {
        val gui = h.api.create("x", 1).setItem(2, guiItem { material = Material.STONE })
        gui.open(alex)
        gui.removeItem(2)
        gui.refresh()
        val update = h.protocol.opsOf<Op.Update>().single()
        assertTrue(update.changes.containsKey(2))
        assertNull(update.changes[2])
    }

    @Test
    fun `many changes are sent as one full resync`() {
        val gui = h.api.create("x", 3)
        gui.open(alex)
        h.protocol.ops.clear()
        gui.fill(guiItem { material = Material.STONE })
        gui.refresh()
        assertEquals(1, h.protocol.opsOf<Op.Resync>().size)
        assertTrue(h.protocol.opsOf<Op.Update>().isEmpty())
    }

    @Test
    fun `refreshSlot updates one slot`() {
        val gui = h.api.create("x", 1).setItem(1, guiItem { material = Material.STONE })
        gui.open(alex)
        gui.setItem(1, guiItem { material = Material.DIAMOND })
        gui.setItem(2, guiItem { material = Material.DIAMOND })
        gui.refreshSlot(1)
        assertEquals(setOf(1), h.protocol.opsOf<Op.Update>().single().changes.keys)
    }

    @Test
    fun `changing the title re-opens the window at the next refresh`() {
        val gui = h.api.create("<red>One", 1)
        gui.open(alex)
        val id = gui.containerIdFor()
        gui.title("<blue>Two")
        gui.refresh()
        val opens = h.protocol.opsOf<Op.Open>()
        assertEquals(2, opens.size)
        assertEquals(id, opens.last().window.containerId, "same window id: the client swaps the title in place")
        assertNotEquals(opens.first().window.title, opens.last().window.title)
    }

    @Test
    fun `state ids increase with every sent packet and wrap inside 15 bits`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        repeat(40000) {
            gui.setItem(0, guiItem { material = Material.STONE; amount = 1 + it % 90 })
            gui.refresh()
        }
        val ids = h.protocol.ops.map {
            when (it) {
                is Op.Open -> it.window.stateId
                is Op.Update -> it.stateId
                is Op.Resync -> it.window.stateId
                else -> 0
            }
        }
        assertTrue(ids.all { it in 0..0x7FFF })
        assertTrue(ids.distinct().size > 1000)
    }

    // ---- closing -------------------------------------------------------------------------------

    @Test
    fun `plugin close sends a close packet and fires GuiCloseEvent`() {
        val gui = h.api.create("x", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        gui.onClose { closes += it }
        gui.open(alex)
        assertTrue(gui.close(alex))
        assertEquals(gui.containerIdFor(), h.protocol.opsOf<Op.Close>().single().containerId)
        assertEquals(GuiCloseReason.PLUGIN, closes.single().reason)
        assertFalse(gui.isViewedBy(alex))
        assertFalse(gui.close(alex), "closing twice is a no-op")
        assertEquals(1, closes.size)
    }

    @Test
    fun `closing a gui the player does not view does nothing`() {
        val gui = h.api.create("x", 1)
        val other = h.api.create("y", 1)
        other.open(alex)
        assertFalse(gui.close(alex))
        assertTrue(other.isViewedBy(alex))
    }

    @Test
    fun `closeAll closes every viewer`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        gui.open(bea)
        gui.closeAll()
        assertTrue(gui.viewers.isEmpty())
        assertEquals(2, h.protocol.opsOf<Op.Close>().size)
    }

    @Test
    fun `closeGui closes whatever the player views`() {
        h.api.create("x", 1).open(alex)
        assertTrue(h.api.closeGui(alex))
        assertFalse(h.api.closeGui(alex))
        assertNull(h.api.currentGui(alex))
    }

    @Test
    fun `a client close is consumed and reported without sending a packet back`() {
        val gui = h.api.create("x", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        gui.onClose { closes += it }
        gui.open(alex)
        val id = gui.containerIdFor()
        assertEquals(PacketVerdict.CONSUME, h.protocol.handler.onClose(alex.uniqueId, id))
        assertEquals(GuiCloseReason.CLIENT, closes.single().reason)
        assertTrue(h.protocol.opsOf<Op.Close>().isEmpty())
        assertFalse(gui.isViewedBy(alex))
    }

    @Test
    fun `close packets for real backend windows pass through`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        assertEquals(PacketVerdict.PASS, h.protocol.handler.onClose(alex.uniqueId, 3))
        assertTrue(gui.isViewedBy(alex))
    }

    @Test
    fun `close packets for a stale proxy window are swallowed`() {
        assertEquals(PacketVerdict.CONSUME, h.protocol.handler.onClose(alex.uniqueId, ContainerIds.FIRST + 5))
    }

    @Test
    fun `disconnect cleans up sessions and per-player caches`() {
        val gui = h.api.create("x", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        gui.onClose { closes += it }
        gui.open(alex)
        h.manager.onDisconnect(alex.uniqueId)
        assertEquals(GuiCloseReason.DISCONNECT, closes.single().reason)
        assertEquals(0, h.manager.sessionCount)
        assertTrue(gui.viewers.isEmpty())
        assertEquals(listOf(alex.uniqueId), h.protocol.forgotten)
        assertTrue(h.protocol.opsOf<Op.Close>().isEmpty(), "never write to a disconnected client")
    }

    @Test
    fun `a server switch drops the session without writing to the client`() {
        val gui = h.api.create("x", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        gui.onClose { closes += it }
        gui.open(alex)
        h.manager.onServerSwitch(alex.uniqueId)
        assertEquals(GuiCloseReason.SERVER_SWITCH, closes.single().reason)
        assertTrue(h.protocol.opsOf<Op.Close>().isEmpty())
        assertEquals(0, h.manager.sessionCount)
    }

    @Test
    fun `when the backend opens its own container our session ends`() {
        val gui = h.api.create("x", 1)
        val closes = mutableListOf<GuiCloseEvent>()
        gui.onClose { closes += it }
        gui.open(alex)
        h.protocol.handler.onBackendContainerOpened(alex.uniqueId)
        assertEquals(GuiCloseReason.BACKEND_CONTAINER, closes.single().reason)
        h.protocol.handler.onBackendContainerOpened(bea.uniqueId) // no session: harmless
    }

    @Test
    fun `shutdown closes everything and unbinds from the protocol`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        gui.open(bea)
        h.manager.shutdown()
        assertEquals(0, h.manager.sessionCount)
        assertEquals(2, h.protocol.opsOf<Op.Close>().size)
        assertTrue(h.protocol.unbound)
    }

    // ---- clicks --------------------------------------------------------------------------------

    private fun openWithButton(onClick: () -> Unit = {}): Pair<Gui, Int> {
        val gui =
            h.api.create("x", 3).setItem(
                13,
                guiItem {
                    material = Material.DIAMOND
                    onClick { onClick() }
                    onClick(GuiClickType.DROP, GuiClickType.NUMBER_KEY, GuiClickType.OFFHAND_SWAP) { execute(GuiAction.Close) }
                },
            )
        gui.open(alex)
        return gui to gui.containerIdFor()
    }

    @Test
    fun `a left click on an item is consumed re-synced and runs the item handler`() {
        var clicked = 0
        val (gui, id) = openWithButton { clicked++ }
        val events = mutableListOf<GuiClickEvent>()
        gui.onClick { events += it }

        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13, 0, RawClickMode.PICKUP, CursorItem(Material.STONE, 3)))

        assertEquals(1, clicked)
        val resync = h.protocol.opsOf<Op.Resync>().single()
        assertEquals(id, resync.window.containerId)
        val event = events.single()
        assertSame(alex, event.player)
        assertSame(gui, event.gui)
        assertEquals(13, event.slot)
        assertEquals(SlotArea.GUI, event.area)
        assertTrue(event.isInGui)
        assertEquals(Material.DIAMOND, event.item!!.material)
        assertEquals(GuiClickType.LEFT, event.clickType)
        assertEquals(CursorItem(Material.STONE, 3), event.cursorItem)
        assertEquals(GuiPlatform.JAVA, event.platform)
    }

    @Test
    fun `right and shift clicks also run the default handler`() {
        var clicked = 0
        val (_, id) = openWithButton { clicked++ }
        h.protocol.click(alex, id, 13, 1)
        h.protocol.click(alex, id, 13, 0, RawClickMode.QUICK_MOVE)
        h.protocol.click(alex, id, 13, 1, RawClickMode.QUICK_MOVE)
        assertEquals(3, clicked)
    }

    @ParameterizedTest
    @EnumSource(value = RawClickMode::class, names = ["CLONE", "PICKUP_ALL", "UNKNOWN"])
    fun `other click kinds never run the default handler but are still reverted`(mode: RawClickMode) {
        var clicked = 0
        val (_, id) = openWithButton { clicked++ }
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13, 2, mode))
        assertEquals(0, clicked)
        assertEquals(1, h.protocol.opsOf<Op.Resync>().size)
    }

    @Test
    fun `number key drop and offhand clicks reach handlers that ask for them`() {
        val (gui, id) = openWithButton()
        val types = mutableListOf<GuiClickType>()
        gui.onClick { types += it.clickType }
        h.protocol.click(alex, id, 13, 3, RawClickMode.SWAP)
        h.protocol.click(alex, id, 13, 40, RawClickMode.SWAP)
        h.protocol.click(alex, id, 13, 0, RawClickMode.THROW)
        assertEquals(listOf(GuiClickType.NUMBER_KEY, GuiClickType.OFFHAND_SWAP, GuiClickType.DROP), types)
        // each of them ran GuiAction.Close through the (recording) executor
        assertEquals(3, h.actions.executed.size)
    }

    @Test
    fun `the hotbar slot of a number key click is exposed`() {
        val (gui, id) = openWithButton()
        var hotbar: Int? = null
        gui.onClick { hotbar = it.hotbarSlot }
        h.protocol.click(alex, id, 13, 6, RawClickMode.SWAP)
        assertEquals(6, hotbar)
    }

    @Test
    fun `cancelling the event stops the item action but the click is still reverted`() {
        var clicked = 0
        val (gui, id) = openWithButton { clicked++ }
        gui.onClick { it.cancelled = true }
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13))
        assertEquals(0, clicked)
        assertEquals(1, h.protocol.opsOf<Op.Resync>().size, "cancelled or not, the client is snapped back")
    }

    @Test
    fun `a global handler can cancel too`() {
        var clicked = 0
        val (_, id) = openWithButton { clicked++ }
        h.api.events.on<GuiClickEvent> { it.cancelled = true }
        h.protocol.click(alex, id, 13)
        assertEquals(0, clicked)
    }

    @Test
    fun `clicks on empty slots and the player's inventory raise events but run no item`() {
        val (gui, id) = openWithButton()
        val events = mutableListOf<GuiClickEvent>()
        gui.onClick { events += it }
        h.protocol.click(alex, id, 2)
        h.protocol.click(alex, id, 30, 0, RawClickMode.QUICK_MOVE)
        h.protocol.click(alex, id, -999, 0)
        assertEquals(listOf(SlotArea.GUI, SlotArea.PLAYER_INVENTORY, SlotArea.OUTSIDE), events.map { it.area })
        assertNull(events[0].item)
        assertFalse(events[1].isInGui)
        assertTrue(events[0].isInGui)
        assertEquals(GuiClickType.OUTSIDE_LEFT, events[2].clickType)
        assertEquals(3, h.protocol.opsOf<Op.Resync>().size)
        assertTrue(h.actions.executed.isEmpty())
    }

    @Test
    fun `an exception in a handler is contained`() {
        val (gui, id) = openWithButton { error("handler bug") }
        val after = mutableListOf<GuiClickEvent>()
        h.api.events.on<GuiClickEvent> { after += it }
        gui.onClick { error("gui handler bug") }
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13))
        assertEquals(1, after.size)
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13), "still working afterwards")
    }

    @Test
    fun `clicks for other windows pass through to the backend`() {
        val (_, _) = openWithButton()
        assertEquals(PacketVerdict.PASS, h.protocol.click(alex, 2, 5))
        assertEquals(PacketVerdict.PASS, h.protocol.click(bea, 2, 5))
        assertEquals(PacketVerdict.PASS, h.protocol.click(alex, 0, 5))
    }

    @Test
    fun `late clicks for a proxy window that is already closed are swallowed`() {
        val (gui, id) = openWithButton()
        gui.close(alex)
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, id, 13))
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(bea, ContainerIds.LAST, 13))
    }

    @Test
    fun `two viewers of one gui click independently`() {
        var clicks = mutableListOf<String>()
        val gui = h.api.create("x", 1).setItem(0, guiItem { material = Material.STONE; onClick { clicks += player.username } })
        gui.open(alex)
        gui.open(bea)
        val alexId = h.protocol.opsOf<Op.Open>().first { it.player === alex }.window.containerId
        val beaId = h.protocol.opsOf<Op.Open>().first { it.player === bea }.window.containerId
        h.protocol.click(bea, beaId, 0)
        h.protocol.click(alex, alexId, 0)
        assertEquals(listOf("Bea", "Alex"), clicks)
        // a click carrying the wrong player's window id is not for that player's window
        assertEquals(PacketVerdict.CONSUME, h.protocol.click(alex, ContainerIds.LAST, 0))
    }

    @Test
    fun `clicks are dispatched to platform specific item variants`() {
        val bedrock = GuiTestHarness(platforms = { GuiPlatform.BEDROCK })
        var seen: String? = null
        val gui =
            bedrock.api.create("x", 1).setItem(
                0,
                guiItem {
                    material = Material.STONE
                    name = "java"
                    forPlatform(GuiPlatform.BEDROCK) { name = "bedrock" }
                    onClick { seen = event.item!!.name.toString() }
                },
            )
        gui.open(alex)
        bedrock.protocol.click(alex, gui.run { bedrock.protocol.opsOf<Op.Open>().last().window.containerId }, 0)
        assertTrue("bedrock" in seen!!)
    }

    // ---- drag ----------------------------------------------------------------------------------

    @Test
    fun `a drag is reported once at its end and only re-synced then`() {
        val gui = h.api.create("x", 3)
        gui.open(alex)
        val id = gui.containerIdFor()
        val drags = mutableListOf<GuiDragEvent>()
        val clicks = mutableListOf<GuiClickEvent>()
        gui.onDrag { drags += it }
        gui.onClick { clicks += it }

        h.protocol.click(alex, id, -999, 0, RawClickMode.QUICK_CRAFT) // start (left)
        h.protocol.click(alex, id, 3, 1, RawClickMode.QUICK_CRAFT)
        h.protocol.click(alex, id, 4, 1, RawClickMode.QUICK_CRAFT)
        h.protocol.click(alex, id, 30, 1, RawClickMode.QUICK_CRAFT)
        assertTrue(h.protocol.opsOf<Op.Resync>().isEmpty(), "no re-sync in the middle of a drag")
        h.protocol.click(alex, id, -999, 2, RawClickMode.QUICK_CRAFT, CursorItem(Material.STONE, 2)) // end

        val drag = drags.single()
        assertEquals(GuiClickType.DRAG_LEFT, drag.clickType)
        assertEquals(setOf(3, 4), drag.guiSlots)
        assertEquals(setOf(3), drag.inventorySlots)
        assertEquals(1, h.protocol.opsOf<Op.Resync>().size)
        assertTrue(clicks.isEmpty(), "drag steps are not clicks")
    }

    @Test
    fun `a cancelled drag is still reverted`() {
        val gui = h.api.create("x", 1)
        gui.open(alex)
        val id = gui.containerIdFor()
        gui.onDrag { it.cancelled = true }
        h.protocol.click(alex, id, -999, 4, RawClickMode.QUICK_CRAFT)
        h.protocol.click(alex, id, 1, 5, RawClickMode.QUICK_CRAFT)
        h.protocol.click(alex, id, -999, 6, RawClickMode.QUICK_CRAFT)
        assertEquals(1, h.protocol.opsOf<Op.Resync>().size)
    }

    // ---- events / actions ----------------------------------------------------------------------

    @Test
    fun `global handlers run before per-gui handlers before item handlers`() {
        val order = mutableListOf<String>()
        val gui = h.api.create("x", 1).setItem(0, guiItem { material = Material.STONE; onClick { order += "item" } })
        gui.onClick { order += "gui" }
        h.api.events.on<GuiClickEvent> { order += "global" }
        gui.open(alex)
        h.protocol.click(alex, gui.containerIdFor(), 0)
        assertEquals(listOf("global", "gui", "item"), order)
    }

    @Test
    fun `registrations can be closed`() {
        val gui = h.api.create("x", 1)
        var count = 0
        val registration = h.api.events.on<GuiOpenEvent> { count++ }
        gui.open(alex)
        registration.close()
        registration.close()
        gui.open(alex)
        assertEquals(1, count)
    }

    @Test
    fun `click shortcuts build the matching actions`() {
        val gui =
            h.api.create("x", 1).setItem(
                0,
                guiItem {
                    material = Material.STONE
                    onClick {
                        executeServerCommand("spawn")
                        executeServerCommand("warp home", "survival")
                        executePlayerCommand("/help")
                        executeProxyCommand("glist")
                        connect("lobby")
                        sendMessage("<green>hi")
                        close()
                        refresh()
                    }
                },
            )
        gui.open(alex)
        h.protocol.click(alex, gui.containerIdFor(), 0)
        val actions = h.actions.executed.map { it.first }
        assertEquals(
            listOf<GuiAction>(
                GuiAction.ExecuteServerCommand("spawn"),
                GuiAction.ExecuteServerCommand("warp home", "survival"),
                GuiAction.ExecutePlayerCommand("help"),
                GuiAction.ExecuteProxyCommand("glist"),
                GuiAction.ConnectToServer("lobby"),
                GuiAction.SendMessage("<green>hi"),
                GuiAction.Close,
                GuiAction.Refresh,
            ),
            actions,
        )
        assertTrue(h.actions.executed.all { it.second === alex })
        assertTrue(h.actions.events.all { it != null })
    }

    @Test
    fun `item action declares a declarative action`() {
        val gui = h.api.create("x", 1).setItem(0, guiItem { material = Material.STONE; action(GuiAction.ConnectToServer("lobby")) })
        gui.open(alex)
        h.protocol.click(alex, gui.containerIdFor(), 0)
        assertEquals(GuiAction.ConnectToServer("lobby"), h.actions.executed.single().first)
    }

    @Test
    fun `events for one player are delivered on one serial queue even with a real executor`() {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val real = GuiTestHarness(executor = pool)
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val done = java.util.concurrent.CountDownLatch(200)
        val gui = real.api.create("x", 1)
        gui.setItem(0, guiItem { material = Material.STONE })
        gui.onClick {
            seen += it.slot
            done.countDown()
        }
        gui.open(alex)
        val id = real.protocol.opsOf<Op.Open>().single().window.containerId
        repeat(200) { i -> real.protocol.click(alex, id, if (i % 2 == 0) 0 else 1) }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(List(200) { if (it % 2 == 0) 0 else 1 }, seen, "order preserved")
        pool.shutdownNow()
    }

    @Test
    fun `the gui extension creates and configures in one go`() {
        val gui = h.api.gui("<gold>T", rows = 2) { setItem(0, guiItem { material = Material.STONE }) }
        assertEquals(18, gui.size)
        assertEquals(Material.STONE, gui.getItem(0)!!.material)
        assertNotEquals(Component.empty(), gui.title)
    }
}

private fun Material.Companion.DIRT_OR(id: String) = of(id)
