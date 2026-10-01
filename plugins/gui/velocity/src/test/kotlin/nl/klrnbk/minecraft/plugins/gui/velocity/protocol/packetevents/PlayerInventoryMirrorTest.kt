package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerInventoryMirrorTest {
    init {
        PeTestSupport.install()
    }

    private val mirror = PlayerInventoryMirror()
    private val player = UUID.randomUUID()

    private fun stack(n: Int) = ItemStack.builder().type(ItemTypes.STONE).amount(n).build()

    @Test
    fun `unknown players have an empty inventory`() {
        assertEquals(36, mirror.playerSlots(player).size)
        assertTrue(mirror.playerSlots(player).all { it.isEmpty })
        assertFalse(mirror.known(player))
    }

    @Test
    fun `main inventory then hotbar map to the 36 player slots`() {
        val all = List(46) { if (it in 9..44) stack(it) else ItemStack.EMPTY }
        mirror.replaceAll(player, all)
        val slots = mirror.playerSlots(player)
        assertEquals(9, slots.first().amount, "first player slot is container slot 9")
        assertEquals(35, slots[26].amount, "last main slot")
        assertEquals(36, slots[27].amount, "first hotbar slot")
        assertEquals(44, slots.last().amount, "last hotbar slot")
    }

    @Test
    fun `crafting armour and offhand slots are not part of the window`() {
        mirror.replaceAll(player, List(46) { stack(it + 1) }) // amount = container slot + 1
        val amounts = mirror.playerSlots(player).map { it.amount }
        assertEquals((10..45).toList(), amounts, "container slots 9..44 only: no craft grid (1-5), armour (6-9) or offhand (46)")
    }

    @Test
    fun `single slot updates apply and ignore out of range slots`() {
        mirror.set(player, 36, stack(7))
        mirror.set(player, 999, stack(1))
        mirror.set(player, -1, stack(1))
        assertEquals(7, mirror.playerSlots(player)[27].amount)
        assertEquals(1, mirror.size)
    }

    @Test
    fun `a short content list is padded with empty stacks`() {
        mirror.replaceAll(player, listOf(stack(1)))
        assertTrue(mirror.playerSlots(player).all { it.isEmpty })
        assertTrue(mirror.known(player))
    }

    @Test
    fun `legacy window minus 2 indexes hotbar first`() {
        assertEquals(36, PlayerInventoryMirror.containerZeroSlotOfInventoryIndex(0))
        assertEquals(44, PlayerInventoryMirror.containerZeroSlotOfInventoryIndex(8))
        assertEquals(9, PlayerInventoryMirror.containerZeroSlotOfInventoryIndex(9))
        assertEquals(35, PlayerInventoryMirror.containerZeroSlotOfInventoryIndex(35))
    }

    @Test
    fun `forget releases the memory`() {
        mirror.set(player, 9, stack(1))
        mirror.forget(player)
        assertEquals(0, mirror.size)
    }
}
