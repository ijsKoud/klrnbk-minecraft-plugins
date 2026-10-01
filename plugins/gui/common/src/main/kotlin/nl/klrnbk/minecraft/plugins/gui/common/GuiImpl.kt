package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiItemProvider
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.GuiRenderContext
import nl.klrnbk.minecraft.plugins.gui.api.GuiState
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiCloseEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiDragEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiOpenEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiRegistration
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The implementation of [Gui].
 *
 * **Concurrency design.** The slot contents are an immutable array held in an [AtomicReference]
 * (copy-on-write; a GUI has at most 54 slots, so copying is cheaper than any locking scheme readers
 * would need). Readers — rendering for a viewer, `getItem` — never lock and always see a consistent
 * snapshot. Writers serialise on [writeLock], which is also what makes [update] atomic: while a
 * batch runs, the writing thread edits a private copy and publishes it in one step.
 * Viewer bookkeeping lives in a [ConcurrentHashMap].
 */
class GuiImpl(
    private val manager: GuiManager,
    override val layout: GuiLayout,
    initialTitle: Component,
    private val logger: Logger,
) : Gui {
    private sealed interface Content {
        class Fixed(
            val item: GuiItem,
        ) : Content

        class Dynamic(
            val provider: GuiItemProvider,
        ) : Content
    }

    override val id: UUID = UUID.randomUUID()
    override val size: Int = layout.size

    private val slots = AtomicReference<Array<Content?>>(arrayOfNulls(size))
    private val writeLock = ReentrantLock()

    /** Non-null only while the lock-holding thread runs [update]. Guarded by [writeLock]. */
    private var staged: Array<Content?>? = null

    private val titleRef = AtomicReference(initialTitle)

    override var title: Component
        get() = titleRef.get()
        set(value) {
            titleRef.set(value)
        }

    /** Maintained by the [GuiManager]; keyed by player uuid. */
    internal val viewerSessions = ConcurrentHashMap<UUID, ViewerSession>()

    private val openHandlers = CopyOnWriteArrayList<(GuiOpenEvent) -> Unit>()
    private val closeHandlers = CopyOnWriteArrayList<(GuiCloseEvent) -> Unit>()
    private val clickHandlers = CopyOnWriteArrayList<(GuiClickEvent) -> Unit>()
    private val dragHandlers = CopyOnWriteArrayList<(GuiDragEvent) -> Unit>()

    // ---- items -------------------------------------------------------------------------------

    private fun checkSlot(slot: Int) {
        if (slot !in 0 until size) throw IndexOutOfBoundsException("slot $slot is outside 0..${size - 1}")
    }

    private fun mutate(change: (Array<Content?>) -> Unit) {
        writeLock.withLock {
            val target = staged
            if (target != null) {
                change(target)
            } else {
                slots.set(slots.get().copyOf().also(change))
            }
        }
    }

    private fun current(): Array<Content?> = if (writeLock.isHeldByCurrentThread) staged ?: slots.get() else slots.get()

    override fun title(miniMessage: String): Gui = apply { title = MiniMessage.miniMessage().deserialize(miniMessage) }

    override fun setItem(
        slot: Int,
        item: GuiItem?,
    ): Gui {
        checkSlot(slot)
        mutate { it[slot] = item?.let(Content::Fixed) }
        return this
    }

    override fun setItem(
        slot: Int,
        provider: GuiItemProvider,
    ): Gui {
        checkSlot(slot)
        mutate { it[slot] = Content.Dynamic(provider) }
        return this
    }

    override fun setItems(
        slots: Iterable<Int>,
        item: GuiItem,
    ): Gui {
        val targets = slots.toList()
        targets.forEach(::checkSlot)
        val content = Content.Fixed(item)
        mutate { array -> targets.forEach { array[it] = content } }
        return this
    }

    override fun getItem(slot: Int): GuiItem? {
        checkSlot(slot)
        return (current()[slot] as? Content.Fixed)?.item
    }

    override fun removeItem(slot: Int): Gui = setItem(slot, null as GuiItem?)

    override fun clear(): Gui {
        mutate { it.fill(null) }
        return this
    }

    override fun fill(item: GuiItem): Gui {
        val content = Content.Fixed(item)
        mutate { it.fill(content) }
        return this
    }

    override fun fillEmpty(item: GuiItem): Gui {
        val content = Content.Fixed(item)
        mutate { array -> for (i in array.indices) if (array[i] == null) array[i] = content }
        return this
    }

    override fun update(block: Gui.() -> Unit): Gui {
        writeLock.withLock {
            if (staged != null) {
                block() // nested update: join the outer batch
                return this
            }
            val copy = slots.get().copyOf()
            staged = copy
            try {
                block()
                slots.set(copy)
            } finally {
                staged = null
            }
        }
        refresh()
        return this
    }

    /** Renders every slot for one viewer. Runs dynamic providers, so it must not be called while holding locks. */
    internal fun render(
        player: Player,
        platform: GuiPlatform,
        state: GuiState,
    ): List<GuiItem?> {
        val snapshot = slots.get()
        return List(size) { slot ->
            when (val content = snapshot[slot]) {
                null -> {
                    null
                }

                is Content.Fixed -> {
                    content.item.forPlatform(platform)
                }

                is Content.Dynamic -> {
                    try {
                        content.provider.provide(GuiRenderContext(player, platform, state, this, slot))?.forPlatform(platform)
                    } catch (t: Throwable) {
                        logger.error("The item provider of slot {} in GUI {} threw", slot, id, t)
                        null
                    }
                }
            }
        }
    }

    // ---- viewers -----------------------------------------------------------------------------

    override fun open(player: Player): GuiOpenResult = manager.open(this, player)

    override fun close(player: Player): Boolean = manager.close(this, player, GuiCloseReason.PLUGIN)

    override fun closeAll() {
        viewerSessions.values.toList().forEach { manager.closeSession(it, GuiCloseReason.PLUGIN, notifyClient = true) }
    }

    override val viewers: Set<Player> get() = viewerSessions.values.mapTo(linkedSetOf()) { it.player }

    override fun isViewedBy(player: Player): Boolean = viewerSessions.containsKey(player.uniqueId)

    override fun state(player: Player): GuiState? = viewerSessions[player.uniqueId]?.state

    override fun refresh() {
        viewerSessions.values.forEach(manager::refresh)
    }

    override fun refresh(player: Player) {
        viewerSessions[player.uniqueId]?.let(manager::refresh)
    }

    override fun refreshSlot(slot: Int) {
        checkSlot(slot)
        viewerSessions.values.forEach { manager.refreshSlot(it, slot) }
    }

    // ---- listeners ---------------------------------------------------------------------------

    private fun <T> CopyOnWriteArrayList<T>.register(handler: T): GuiRegistration {
        add(handler)
        return GuiRegistration { remove(handler) }
    }

    override fun onOpen(handler: (GuiOpenEvent) -> Unit): GuiRegistration = openHandlers.register(handler)

    override fun onClose(handler: (GuiCloseEvent) -> Unit): GuiRegistration = closeHandlers.register(handler)

    override fun onClick(handler: (GuiClickEvent) -> Unit): GuiRegistration = clickHandlers.register(handler)

    override fun onDrag(handler: (GuiDragEvent) -> Unit): GuiRegistration = dragHandlers.register(handler)

    internal fun fireOpen(event: GuiOpenEvent) = fire(openHandlers, event)

    internal fun fireClose(event: GuiCloseEvent) = fire(closeHandlers, event)

    internal fun fireClick(event: GuiClickEvent) = fire(clickHandlers, event)

    internal fun fireDrag(event: GuiDragEvent) = fire(dragHandlers, event)

    private fun <E> fire(
        handlers: List<(E) -> Unit>,
        event: E,
    ) {
        for (handler in handlers) {
            try {
                handler(event)
            } catch (t: Throwable) {
                logger.error("A handler of GUI {} threw for {}", id, event!!::class.simpleName, t)
            }
        }
    }

    override fun toString(): String = "Gui($layout, id=$id)"
}
