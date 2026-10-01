package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.item.type.ItemType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType
import nl.klrnbk.minecraft.plugins.gui.api.event.CursorItem
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode

/**
 * Decodes PacketEvents' "click container" wrapper into the protocol-neutral [RawClick].
 *
 * Since 1.21.5 the packet carries *hashed* stacks (item type, count and a hash per changed component), not full
 * item stacks, so the cursor we can report is limited to type and amount. Older layouts (full stacks) are
 * handled too, because PacketEvents exposes both and picks by the client's version.
 */
internal object PeClickDecoder {
    fun decode(packet: WrapperPlayClientClickWindow): RawClick =
        RawClick(
            containerId = packet.windowId,
            stateId = packet.stateId.orElse(0),
            slot = packet.slot,
            button = packet.button,
            mode = mode(packet.windowClickType),
            cursor = cursor(packet),
        )

    fun mode(type: WindowClickType?): RawClickMode =
        when (type) {
            WindowClickType.PICKUP -> RawClickMode.PICKUP
            WindowClickType.QUICK_MOVE -> RawClickMode.QUICK_MOVE
            WindowClickType.SWAP -> RawClickMode.SWAP
            WindowClickType.CLONE -> RawClickMode.CLONE
            WindowClickType.THROW -> RawClickMode.THROW
            WindowClickType.QUICK_CRAFT -> RawClickMode.QUICK_CRAFT
            WindowClickType.PICKUP_ALL -> RawClickMode.PICKUP_ALL
            else -> RawClickMode.UNKNOWN
        }

    private fun cursor(packet: WrapperPlayClientClickWindow): CursorItem? {
        val hashed = packet.carriedHashedStack.orElse(null)
        if (hashed != null) return cursorOf(hashed.item, hashed.count)
        val stack = packet.carriedItemStack
        return if (stack == null || stack.isEmpty) null else cursorOf(stack.type, stack.amount)
    }

    private fun cursorOf(
        type: ItemType,
        count: Int,
    ): CursorItem? = if (count <= 0) null else CursorItem(Material.of(type.name.toString()), count)
}
