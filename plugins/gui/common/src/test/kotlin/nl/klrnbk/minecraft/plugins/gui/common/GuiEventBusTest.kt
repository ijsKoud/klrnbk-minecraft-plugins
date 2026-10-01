package nl.klrnbk.minecraft.plugins.gui.common

import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiOpenEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.CancellableGuiEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.on
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuiEventBusTest {
    private val h = GuiTestHarness()

    @Test
    fun `handlers only receive events of their type, supertypes see everything`() {
        val opens = mutableListOf<GuiEvent>()
        val clicks = mutableListOf<GuiEvent>()
        val all = mutableListOf<GuiEvent>()
        val cancellable = mutableListOf<GuiEvent>()
        h.api.events.on<GuiOpenEvent> { opens += it }
        h.api.events.on<GuiClickEvent> { clicks += it }
        h.api.events.on<GuiEvent> { all += it }
        h.api.events.on<CancellableGuiEvent> { cancellable += it }

        val gui = h.api.create("x", 1)
        val player = testPlayer()
        gui.open(player)
        h.protocol.click(player, h.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.containerId, 0)
        gui.close(player)

        assertEquals(1, opens.size)
        assertEquals(1, clicks.size)
        assertEquals(3, all.size) // open, click, close
        assertEquals(2, cancellable.size) // open, click
    }

    @Test
    fun `a handler that registers or unregisters during dispatch does not disturb it`() {
        var second = 0
        lateinit var registration: nl.klrnbk.minecraft.plugins.gui.api.event.GuiRegistration
        registration = h.api.events.on<GuiOpenEvent> { registration.close() }
        h.api.events.on<GuiOpenEvent> { second++ }
        val gui = h.api.create("x", 1)
        gui.open(testPlayer())
        gui.open(testPlayer())
        assertEquals(2, second)
    }

    @Test
    fun `a throwing handler does not stop the others`() {
        var reached = false
        h.api.events.on<GuiOpenEvent> { error("boom") }
        h.api.events.on<GuiOpenEvent> { reached = true }
        h.api.create("x", 1).open(testPlayer())
        assertEquals(true, reached)
    }
}
