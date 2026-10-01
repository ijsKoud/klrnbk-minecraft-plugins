package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.player.ClientVersion
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout

/**
 * The numeric ids of the `minecraft:menu` registry entries the framework opens, per client version.
 *
 * Menu type ids are *registry ids*, i.e. positions in a list that Mojang can (and, over the years, did)
 * reorder by inserting new menus. PacketEvents has no helper for them, so the table lives here, in one place.
 * The order below has been stable since the 1.20.3 `crafter_3x3` insertion and was re-checked against
 * MCProtocolLib's `ContainerType` for 26.x (see RESEARCH.md). **When a Minecraft release touches the menu
 * registry, this is the one table to update** — [MenuTypeTest] pins it.
 */
internal object MenuTypes {
    /** Registry order of the `minecraft:menu` registry in 26.x; only the entries we use are named. */
    private const val GENERIC_9X1 = 0 // 9x2 = 1 ... 9x6 = 5
    private const val GENERIC_3X3 = 6
    private const val HOPPER = 16

    /** The oldest client version whose menu registry matches [idFor]. Older clients are refused, not guessed at. */
    val MINIMUM_CLIENT: ClientVersion = ClientVersion.V_1_21_5

    fun isSupported(version: ClientVersion): Boolean = version.isNewerThanOrEquals(MINIMUM_CLIENT)

    fun idFor(layout: GuiLayout): Int =
        when (layout) {
            is GuiLayout.Chest -> GENERIC_9X1 + layout.rows - 1
            GuiLayout.Dispenser -> GENERIC_3X3
            GuiLayout.Hopper -> HOPPER
        }
}
