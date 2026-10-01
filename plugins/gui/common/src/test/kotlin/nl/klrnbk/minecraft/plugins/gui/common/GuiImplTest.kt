package nl.klrnbk.minecraft.plugins.gui.common

import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GuiImplTest {
    private val harness = GuiTestHarness()
    private val stone = guiItem { material = Material.STONE }
    private val dirt = guiItem { material = Material.of("dirt") }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6])
    fun `create makes a chest of the requested rows`(rows: Int) {
        val gui = harness.api.create("<gold>Menu", rows)
        assertEquals(rows * 9, gui.size)
        assertEquals(GuiLayout.chest(rows), gui.layout)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 7, -3])
    fun `create rejects invalid row counts`(rows: Int) {
        assertThrows<IllegalArgumentException> { harness.api.create("x", rows) }
    }

    @Test
    fun `hopper and dispenser layouts are supported`() {
        assertEquals(5, harness.api.create("x", GuiLayout.Hopper).size)
        assertEquals(9, harness.api.create("x", GuiLayout.Dispenser).size)
    }

    @Test
    fun `the title is parsed from MiniMessage and can be replaced`() {
        val gui = harness.api.create("<gold>Menu", 3)
        assertEquals(Component.text("Menu", net.kyori.adventure.text.format.NamedTextColor.GOLD), gui.title)
        gui.title("<red>Other")
        assertTrue("Other" in net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(gui.title))
        gui.title = Component.text("Plain")
        assertEquals(Component.text("Plain"), gui.title)
    }

    @Test
    fun `slots start empty`() {
        val gui = harness.api.create("x", 1)
        repeat(9) { assertNull(gui.getItem(it)) }
    }

    @Test
    fun `items can be placed replaced and removed`() {
        val gui = harness.api.create("x", 1)
        gui.setItem(4, stone)
        assertSame(stone, gui.getItem(4))
        gui.setItem(4, dirt)
        assertSame(dirt, gui.getItem(4))
        gui.removeItem(4)
        assertNull(gui.getItem(4))
        gui.setItem(4, stone).setItem(4, null as nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem?)
        assertNull(gui.getItem(4))
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 9, 100])
    fun `out of range slots are rejected everywhere`(slot: Int) {
        val gui = harness.api.create("x", 1)
        assertThrows<IndexOutOfBoundsException> { gui.setItem(slot, stone) }
        assertThrows<IndexOutOfBoundsException> { gui.getItem(slot) }
        assertThrows<IndexOutOfBoundsException> { gui.removeItem(slot) }
        assertThrows<IndexOutOfBoundsException> { gui.setItem(slot) { stone } }
        assertThrows<IndexOutOfBoundsException> { gui.setItems(listOf(0, slot), stone) }
        assertThrows<IndexOutOfBoundsException> { gui.refreshSlot(slot) }
    }

    @Test
    fun `a failed setItems changes nothing`() {
        val gui = harness.api.create("x", 1)
        assertThrows<IndexOutOfBoundsException> { gui.setItems(listOf(0, 1, 99), stone) }
        assertNull(gui.getItem(0))
    }

    @Test
    fun `fill clear and fillEmpty`() {
        val gui = harness.api.create("x", 1)
        gui.setItem(2, dirt)
        gui.fillEmpty(stone)
        assertSame(dirt, gui.getItem(2))
        assertSame(stone, gui.getItem(0))
        gui.fill(stone)
        assertSame(stone, gui.getItem(2))
        gui.clear()
        repeat(9) { assertNull(gui.getItem(it)) }
        gui.setItems(listOf(1, 3, 5), dirt)
        assertSame(dirt, gui.getItem(3))
        assertNull(gui.getItem(4))
    }

    @Test
    fun `dynamic slots have no static item but render per viewer`() {
        val gui = harness.api.create("x", 1)
        gui.setItem(0) { ctx -> guiItem { material = Material.STONE; name = "Hi ${ctx.player.username}" } }
        assertNull(gui.getItem(0))
        val player = testPlayer("Alex")
        assertEquals(nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult.OPENED, gui.open(player))
        val shown = harness.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.items[0]
        assertEquals("Hi Alex", net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(shown!!.name!!))
    }

    @Test
    fun `a throwing item provider renders an empty slot instead of failing the gui`() {
        val gui = harness.api.create("x", 1)
        gui.setItem(0) { error("bad provider") }
        gui.setItem(1, stone)
        gui.open(testPlayer())
        val items = harness.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.items
        assertNull(items[0])
        assertSame(stone, items[1])
    }

    @Test
    fun `platform overrides are applied when rendering for that platform only`() {
        val item =
            guiItem {
                material = Material.DIAMOND
                customModelData = 5
                forPlatform(GuiPlatform.BEDROCK) { customModelData = null }
            }
        val bedrock = GuiTestHarness(platforms = { GuiPlatform.BEDROCK })
        bedrock.api.create("x", 1).setItem(0, item).open(testPlayer())
        assertNull(bedrock.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.items[0]!!.customModelData)

        harness.api.create("x", 1).setItem(0, item).open(testPlayer())
        assertEquals(5, harness.protocol.opsOf<FakeGuiProtocol.Op.Open>().single().window.items[0]!!.customModelData)
    }

    @Test
    fun `update applies a batch atomically and refreshes once`() {
        val gui = harness.api.create("x", 1)
        val player = testPlayer()
        gui.open(player)
        harness.protocol.ops.clear()
        gui.update {
            setItem(0, stone)
            setItem(1, dirt)
            assertSame(stone, getItem(0)) // the batch sees its own writes
        }
        val updates = harness.protocol.opsOf<FakeGuiProtocol.Op.Update>()
        assertEquals(1, updates.size)
        assertEquals(setOf(0, 1), updates.single().changes.keys)
    }

    @Test
    fun `a failing update block publishes nothing`() {
        val gui = harness.api.create("x", 1)
        gui.setItem(0, stone)
        assertThrows<IllegalStateException> {
            gui.update {
                setItem(0, dirt)
                error("abort")
            }
        }
        assertSame(stone, gui.getItem(0))
    }

    @Test
    fun `concurrent writers never lose an update or expose a torn state`() {
        val gui = harness.api.create("x", 6)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) { writer ->
            pool.execute {
                start.await()
                repeat(200) { i -> gui.setItem((writer * 6 + i % 6), stone) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        repeat(48) { assertSame(stone, gui.getItem(it), "slot $it") }
    }

    @Test
    fun `each gui has a unique id`() {
        val a = harness.api.create("x", 1)
        val b = harness.api.create("x", 1)
        assertNotSame(a.id, b.id)
        assertTrue(a.id != b.id)
    }

    @Test
    fun `platform of a player comes from the detector`() {
        val bedrock = GuiTestHarness(platforms = { GuiPlatform.BEDROCK })
        assertEquals(GuiPlatform.BEDROCK, bedrock.api.platformOf(testPlayer()))
        assertEquals(GuiPlatform.JAVA, harness.api.platformOf(testPlayer()))
    }
}
