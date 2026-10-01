package nl.klrnbk.minecraft.plugins.gui.common

import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import nl.klrnbk.minecraft.plugins.gui.common.FakeGuiProtocol.Op
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Hammers the manager from many threads. The assertions are about invariants, not timing. */
@Timeout(60)
class GuiConcurrencyTest {
    private val pool = Executors.newFixedThreadPool(16)

    @Test
    fun `many players opening refreshing and closing one shared gui leave no session behind`() {
        val h = GuiTestHarness(executor = pool)
        val gui = h.api.create("shared", 6)
        val players = List(64) { testPlayer("P$it") }
        val start = CountDownLatch(1)
        val done = CountDownLatch(players.size)
        val failures = AtomicInteger()

        players.forEach { player ->
            pool.execute {
                try {
                    start.await()
                    repeat(30) { round ->
                        check(gui.open(player) == GuiOpenResult.OPENED)
                        gui.setItem(round % 54, guiItem { material = Material.STONE; amount = 1 + round })
                        gui.refresh()
                        gui.refresh(player)
                        if (round % 3 == 0) gui.close(player)
                    }
                    gui.close(player)
                } catch (t: Throwable) {
                    failures.incrementAndGet()
                    t.printStackTrace()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(45, TimeUnit.SECONDS))
        assertEquals(0, failures.get())
        assertEquals(0, h.manager.sessionCount, "no leaked sessions")
        assertTrue(gui.viewers.isEmpty(), "no leaked viewers")
    }

    @Test
    fun `concurrent opens of one player never produce two sessions`() {
        val h = GuiTestHarness(executor = pool)
        val player = testPlayer()
        val guis = List(16) { h.api.create("g$it", 1) }
        val start = CountDownLatch(1)
        val done = CountDownLatch(guis.size)
        guis.forEach { gui ->
            pool.execute {
                start.await()
                repeat(50) { gui.open(player) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertEquals(1, h.manager.sessionCount)
        assertEquals(1, guis.count { it.isViewedBy(player) }, "exactly one gui owns the player")
        h.manager.onDisconnect(player.uniqueId)
        assertEquals(0, h.manager.sessionCount)
        assertTrue(guis.none { it.isViewedBy(player) })
    }

    @Test
    fun `disconnects racing with clicks and refreshes are safe`() {
        val h = GuiTestHarness(executor = pool)
        val gui = h.api.create("x", 1).setItem(0, guiItem { material = Material.STONE; onClick { } })
        val rounds = 300
        val outer = Executors.newFixedThreadPool(8) // separate from `pool`: tasks below block on the racers
        val done = CountDownLatch(rounds)
        val failures = AtomicInteger()
        repeat(rounds) {
            outer.execute {
                val player = testPlayer()
                try {
                    gui.open(player)
                    val id = h.protocol.opsOf<Op.Open>().last { o -> o.player === player }.window.containerId
                    val racer = pool.submit { repeat(5) { h.protocol.click(player, id, 0); gui.refresh(player) } }
                    h.manager.onDisconnect(player.uniqueId)
                    racer.get(20, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                    failures.incrementAndGet()
                    t.printStackTrace()
                } finally {
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(45, TimeUnit.SECONDS))
        outer.shutdown()
        assertEquals(0, failures.get())
        assertEquals(0, h.manager.sessionCount)
        assertTrue(gui.viewers.isEmpty())
    }

    @Test
    fun `close events fire exactly once per session even when close paths race`() {
        val h = GuiTestHarness(executor = pool)
        val gui = h.api.create("x", 1)
        val closed = AtomicInteger()
        gui.onClose { closed.incrementAndGet() }
        val sessions = 200
        val racing = 4
        val done = CountDownLatch(sessions * racing)
        repeat(sessions) {
            val player = testPlayer()
            gui.open(player)
            val id = h.protocol.opsOf<Op.Open>().last { o -> o.player === player }.window.containerId
            listOf<() -> Unit>(
                { gui.close(player) },
                { h.protocol.handler.onClose(player.uniqueId, id) },
                { h.manager.onDisconnect(player.uniqueId) },
                { h.manager.onServerSwitch(player.uniqueId) },
            ).forEach { action ->
                pool.execute {
                    action()
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS)) // drains the queued close events
        assertEquals(sessions, closed.get())
        assertEquals(0, h.manager.sessionCount)
    }
}
