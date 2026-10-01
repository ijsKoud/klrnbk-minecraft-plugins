package nl.klrnbk.minecraft.plugins.gui.common.events

import nl.klrnbk.minecraft.plugins.gui.api.event.GuiEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiEvents
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiRegistration
import org.slf4j.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The framework-wide listener registry.
 *
 * Registration and dispatch are lock-free (copy-on-write list): dispatch is by far the hot path and
 * iterates an immutable snapshot, so a handler may register or unregister listeners — including itself —
 * without affecting the dispatch in progress. A handler that throws is logged and skipped.
 */
class GuiEventBus(
    private val logger: Logger,
) : GuiEvents {
    private class Entry(
        val type: Class<out GuiEvent>,
        val handler: (GuiEvent) -> Unit,
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : GuiEvent> subscribe(
        type: Class<T>,
        handler: (T) -> Unit,
    ): GuiRegistration {
        val entry = Entry(type, handler as (GuiEvent) -> Unit)
        entries += entry
        return GuiRegistration { entries.remove(entry) }
    }

    fun dispatch(event: GuiEvent) {
        for (entry in entries) {
            if (!entry.type.isInstance(event)) continue
            try {
                entry.handler(event)
            } catch (t: Throwable) {
                logger.error("A GUI event handler for {} threw", event.javaClass.simpleName, t)
            }
        }
    }
}
