package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.GuiState
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.common.click.DragTracker
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * One player looking at one GUI. Created on open, dropped on close — everything per-viewer (container
 * id, sync counter, [state], drag progress) lives here and therefore cannot outlive the viewing.
 *
 * Thread-confinement: [drag] is touched only from [serial]; [rendered]/[title] are written under [lock];
 * [closed] is a volatile flag; [refreshLock] serialises whole refreshes (which run user code) so they cannot
 * overtake each other, without ever blocking the netty thread that only needs [lock].
 */
class ViewerSession(
    val player: Player,
    val gui: GuiImpl,
    val containerId: Int,
    val platform: GuiPlatform,
    val state: GuiState,
    val serial: Executor,
    initialItems: List<GuiItem?>,
    initialTitle: Component,
) {
    /** Guards sending: keeps packets of one window in the order their state ids were handed out. */
    val lock = ReentrantLock()
    val refreshLock = Any()

    private val stateCounter = AtomicInteger(0)

    @Volatile
    var rendered: List<GuiItem?> = initialItems

    @Volatile
    var title: Component = initialTitle

    @Volatile
    var closed: Boolean = false

    val drag = DragTracker(gui.size)

    /** Minecraft keeps the container state id in 15 bits. */
    fun nextStateId(): Int = stateCounter.updateAndGet { (it + 1) and STATE_ID_MASK }

    private companion object {
        const val STATE_ID_MASK = 0x7FFF
    }
}
