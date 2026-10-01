package nl.klrnbk.minecraft.plugins.gui.common

import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/** Memory-leak guards: once a viewer is gone, nothing in the manager may keep the GUI, the player or their caches alive. */
class GuiLeakTest {
    private fun gc(ref: WeakReference<*>) {
        repeat(50) {
            if (ref.get() == null) return
            System.gc()
            Thread.sleep(20)
        }
    }

    @Test
    fun `a closed gui and its viewer can be garbage collected while the manager lives on`() {
        val h = GuiTestHarness()
        var gui: nl.klrnbk.minecraft.plugins.gui.api.Gui? = h.api.create("x", 6).also { it.setItem(0, guiItem { material = Material.STONE; onClick { } }) }
        var player: com.velocitypowered.api.proxy.Player? = testPlayer()
        val guiRef = WeakReference(gui)
        val uuid = player!!.uniqueId

        gui!!.open(player)
        h.protocol.click(player, h.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.containerId, 0)
        gui.close(player)
        h.manager.onDisconnect(uuid)

        h.protocol.ops.clear() // the fake protocol's recording log holds windows (and thus items); a real protocol keeps nothing
        gui = null
        player = null
        gc(guiRef)
        assertNull(guiRef.get(), "the manager must not retain closed GUIs")
        assertEquals(0, h.manager.sessionCount)
    }

    @Test
    fun `disconnect forgets the cached platform so a reconnecting uuid is detected afresh`() {
        val calls = AtomicInteger()
        val h = GuiTestHarness(platforms = { calls.incrementAndGet(); GuiPlatform.JAVA })
        val player = testPlayer()
        h.api.platformOf(player)
        h.api.platformOf(player)
        assertEquals(1, calls.get())
        h.manager.onDisconnect(player.uniqueId)
        h.api.platformOf(player)
        assertEquals(2, calls.get())
    }

    @Test
    fun `a viewer that never closes is released by disconnect`() {
        val h = GuiTestHarness()
        val guis = List(50) { h.api.create("g$it", 1) }
        val players = List(50) { testPlayer() }
        guis.zip(players).forEach { (g, p) -> g.open(p) }
        assertEquals(50, h.manager.sessionCount)
        players.forEach { h.manager.onDisconnect(it.uniqueId) }
        assertEquals(0, h.manager.sessionCount)
        assertEquals(50, h.protocol.forgotten.size)
        guis.forEach { assertEquals(emptySet<Any>(), it.viewers) }
    }
}
