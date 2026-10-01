package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import nl.klrnbk.minecraft.plugins.gui.common.protocol.WindowSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.slf4j.helpers.NOPLogger

/**
 * Byte-level checks: the packets the framework builds are serialised with PacketEvents exactly as they would be written
 * to a client, then read back field by field. This pins the *wire format* rather than our own idea of it.
 */
class WireFormatTest {
    init {
        PeTestSupport.install() // must precede the first touch of PacketEvents' static registries
    }

    private val converter = PeItemConverter(NOPLogger.NOP_LOGGER)

    private fun window(layout: GuiLayout, id: Int = 101, state: Int = 7) = WindowSpec(id, state, layout, Component.text("Menu"), List(layout.size) { null })

    @Test
    fun `open screen writes container id then menu type then title`() {
        val reader = PeTestSupport.roundTrip { PacketFactory.openWindow(window(GuiLayout.chest(3))) }
        assertEquals(101, reader.readVarInt())
        assertEquals(2, reader.readVarInt()) // generic_9x3
        assertEquals(Component.text("Menu"), reader.readComponent())
    }

    @Test
    fun `every layout maps to its menu type id`() {
        val expected = mapOf(
            GuiLayout.chest(1) to 0, GuiLayout.chest(2) to 1, GuiLayout.chest(3) to 2,
            GuiLayout.chest(4) to 3, GuiLayout.chest(5) to 4, GuiLayout.chest(6) to 5,
            GuiLayout.Dispenser to 6, GuiLayout.Hopper to 16,
        )
        expected.forEach { (layout, id) ->
            val reader = PeTestSupport.roundTrip { PacketFactory.openWindow(window(layout)) }
            reader.readVarInt()
            assertEquals(id, reader.readVarInt(), "menu type of $layout")
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClientVersion::class, names = ["V_1_21_5", "V_1_21_11", "V_26_1", "V_26_2", "V_26_3"])
    fun `window content carries the gui items then the 36 player slots then an empty cursor`(client: ClientVersion) {
        val diamond = converter.convert(guiItem { material = Material.DIAMOND; name = "<aqua>Spawn"; lore("<gray>Teleport"); amount = 3 }, client)
        val guiStacks = List(27) { if (it == 13) diamond else ItemStack.EMPTY }
        val player = List(36) { if (it == 0) ItemStack.builder().type(ItemTypes.STONE).amount(5).build() else ItemStack.EMPTY }

        val reader = PeTestSupport.roundTrip(client = client) { PacketFactory.windowContent(101, 9, guiStacks, player) }
        assertEquals(101, reader.readVarInt())
        assertEquals(9, reader.readVarInt())
        assertEquals(27 + 36, reader.readVarInt())
        val items = List(27 + 36) { reader.readItemStack() }
        val cursor = reader.readItemStack()

        assertTrue(items[0].isEmpty)
        assertEquals(ItemTypes.DIAMOND, items[13].type)
        assertEquals(3, items[13].amount)
        assertEquals(ItemTypes.STONE, items[27].type, "first player slot follows the last gui slot")
        assertEquals(5, items[27].amount)
        assertTrue(cursor.isEmpty, "the cursor is emptied so client-side predictions are undone")
    }

    @Test
    fun `item name and lore survive the wire as components without default italics`() {
        val client = ClientVersion.V_26_2
        val stack = converter.convert(guiItem { material = Material.DIAMOND; name = "<aqua>Spawn"; lore("<gray>Teleport", "", "<yellow>Click") }, client)
        val read = decodeSetSlotItem(client, stack)
        val name = read.getComponent(ComponentTypes.CUSTOM_NAME).get()
        assertEquals("Spawn", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name))
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE, name.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
        val lore = read.getComponent(ComponentTypes.LORE).get().lines
        assertEquals(3, lore.size)
        assertEquals(Component.empty().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), lore[1])
    }

    private fun decodeSetSlotItem(client: ClientVersion, stack: ItemStack): ItemStack {
        val reader = PeTestSupport.roundTrip(client = client) { PacketFactory.setSlot(101, 4, 13, stack) }
        assertEquals(101, reader.readVarInt()) // container id
        assertEquals(4, reader.readVarInt()) // state id
        assertEquals(13, reader.readShort().toInt()) // slot
        return reader.readItemStack()
    }

    @Test
    fun `close window writes the container id`() {
        val reader = PeTestSupport.roundTrip { PacketFactory.closeWindow(101) }
        assertEquals(101, reader.readVarInt())
    }
}
