package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A copy of what the backend last told each client about the player's own inventory (container 0).
 *
 * **Why this exists.** A chest-style window is a `container` of N slots followed by the player's 36
 * inventory slots, and the "set container content" packet the client accepts for it covers *all* of
 * them. The proxy owns the GUI slots but not the player's items — the backend does. Without a copy,
 * every GUI we open would have to send 36 empty stacks and the client would show (and, for its
 * prediction, believe in) an empty inventory. The mirror is fed from the backend's own container-0
 * packets, which pass through the proxy anyway.
 *
 * Layout of container 0: `0` craft result, `1..4` craft grid, `5..8` armour, `9..35` main inventory,
 * `36..44` hotbar, `45` offhand. In the GUI window the player part is the main inventory followed by the
 * hotbar, i.e. container-0 slots `9..44`.
 *
 * Updates arrive on the player's own netty event loop; readers are other threads, so each player's slots are an
 * immutable array swapped in atomically (copy-on-write).
 */
internal class PlayerInventoryMirror {
    private val inventories = ConcurrentHashMap<UUID, Array<ItemStack>>()

    /** Replaces the whole of container 0 (a "set container content" packet for window 0). */
    fun replaceAll(
        player: UUID,
        items: List<ItemStack>,
    ) {
        val array = Array(CONTAINER_ZERO_SIZE) { index -> items.getOrNull(index) ?: ItemStack.EMPTY }
        inventories[player] = array
    }

    /** Updates one container-0 slot. */
    fun set(
        player: UUID,
        slot: Int,
        item: ItemStack,
    ) {
        if (slot !in 0 until CONTAINER_ZERO_SIZE) return
        inventories.compute(player) { _, current ->
            val copy = current?.copyOf() ?: Array(CONTAINER_ZERO_SIZE) { ItemStack.EMPTY }
            copy[slot] = item
            copy
        }
    }

    /** The 36 player-inventory stacks as they appear below a GUI window, or all-empty if nothing was seen yet. */
    fun playerSlots(player: UUID): List<ItemStack> {
        val current = inventories[player]
        return List(PLAYER_SLOTS) { index -> current?.get(FIRST_MAIN_SLOT + index) ?: ItemStack.EMPTY }
    }

    fun known(player: UUID): Boolean = inventories.containsKey(player)

    fun forget(player: UUID) {
        inventories.remove(player)
    }

    val size: Int get() = inventories.size

    companion object {
        const val CONTAINER_ZERO_SIZE = 46
        const val FIRST_MAIN_SLOT = 9
        const val PLAYER_SLOTS = 36

        /** Container-0 slot of a "player inventory" index (0..8 hotbar, 9..35 main), as used by the legacy window id -2. */
        fun containerZeroSlotOfInventoryIndex(index: Int): Int = if (index in 0..8) 36 + index else index
    }
}
