package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.GuiState
import nl.klrnbk.minecraft.plugins.gui.api.SlotArea
import nl.klrnbk.minecraft.plugins.gui.api.event.CursorItem
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiCloseEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiDragEvent
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiOpenEvent
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.common.actions.GuiActionExecutor
import nl.klrnbk.minecraft.plugins.gui.common.click.ClickInterpreter
import nl.klrnbk.minecraft.plugins.gui.common.click.ClickStep
import nl.klrnbk.minecraft.plugins.gui.common.events.GuiEventBus
import nl.klrnbk.minecraft.plugins.gui.common.platform.CachingPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ContainerIds
import nl.klrnbk.minecraft.plugins.gui.common.protocol.GuiProtocol
import nl.klrnbk.minecraft.plugins.gui.common.protocol.InboundHandler
import nl.klrnbk.minecraft.plugins.gui.common.protocol.PacketVerdict
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.WindowSpec
import nl.klrnbk.minecraft.plugins.gui.common.util.SerialExecutor
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.concurrent.withLock

/**
 * Owns every open GUI session and turns protocol traffic into events.
 *
 * ## Threading model
 * - **Sessions** live in a [ConcurrentHashMap] keyed by player uuid. Replacing a player's session (opening a
 *   GUI while another is open) happens inside `compute`, so a player has at most one session and
 *   two concurrent `open` calls cannot both win.
 * - **Inbound packets** arrive on the player's netty event loop. That thread does the minimum — decode,
 *   answer with a re-sync built from the *last rendered snapshot* (no user code runs there) and enqueue the
 *   event on the session's [SerialExecutor] — so slow handlers can never stall the connection.
 * - **Event handlers** run on the shared [eventExecutor], serialised per player: in order, never
 *   concurrently for one player, in parallel across players.
 * - **Outbound packets** for one session are sent while holding that session's `lock` so the state ids they
 *   carry reach the client in order. Sending is non-blocking (netty enqueues the write), so the lock is held for
 *   microseconds.
 * - **Refreshes** additionally serialise on the session's `refreshLock`, because they call user
 *   [nl.klrnbk.minecraft.plugins.gui.api.GuiItemProvider]s; the netty thread never takes that lock.
 */
class GuiManager(
    private val protocol: GuiProtocol,
    private val platforms: CachingPlatformDetector,
    val events: GuiEventBus,
    private val eventExecutor: Executor,
    private val logger: Logger,
    actionsFactory: (GuiManager) -> GuiActionExecutor,
) : InboundHandler {
    private val sessions = ConcurrentHashMap<UUID, ViewerSession>()
    val actions: GuiActionExecutor = actionsFactory(this)

    init {
        protocol.bind(this)
    }

    val protocolDescription: String get() = protocol.description

    fun currentSession(player: Player): ViewerSession? = sessions[player.uniqueId]

    fun platformOf(player: Player): GuiPlatform = platforms.detect(player)

    /** Number of open sessions (diagnostics and leak tests). */
    val sessionCount: Int get() = sessions.size

    // ---- opening -----------------------------------------------------------------------------

    fun open(
        gui: GuiImpl,
        player: Player,
    ): GuiOpenResult {
        if (!player.isActive) return GuiOpenResult.PLAYER_DISCONNECTED
        when (protocol.check(player)) {
            ProtocolCheck.OK -> {}
            ProtocolCheck.PLAYER_DISCONNECTED -> return GuiOpenResult.PLAYER_DISCONNECTED
            ProtocolCheck.NOT_IN_PLAY_PHASE -> return GuiOpenResult.NOT_IN_PLAY_PHASE
            ProtocolCheck.UNSUPPORTED_CLIENT -> return GuiOpenResult.UNSUPPORTED_CLIENT
        }

        val platform = platforms.detect(player)
        val openEvent = GuiOpenEvent(player, gui, platform)
        events.dispatch(openEvent)
        gui.fireOpen(openEvent)
        if (openEvent.cancelled) return GuiOpenResult.CANCELLED

        // User code (dynamic item providers) runs here, deliberately outside of compute().
        val state = GuiState()
        val items = gui.render(player, platform, state)
        val title = gui.title

        var result = GuiOpenResult.OPENED
        var replaced: ViewerSession? = null
        sessions.compute(player.uniqueId) { _, old ->
            val session =
                ViewerSession(
                    player = player,
                    gui = gui,
                    containerId = ContainerIds.next(old?.containerId),
                    platform = platform,
                    state = state,
                    serial = SerialExecutor(eventExecutor, logger),
                    initialItems = items,
                    initialTitle = title,
                )
            try {
                session.lock.withLock {
                    protocol.open(player, WindowSpec(session.containerId, session.nextStateId(), gui.layout, title, items))
                }
            } catch (t: Throwable) {
                logger.error("Failed to open GUI {} for {}", gui.id, player.username, t)
                result = GuiOpenResult.PROTOCOL_ERROR
                return@compute old
            }
            gui.viewerSessions[player.uniqueId] = session
            replaced = old
            session
        }
        replaced?.let { retire(it, GuiCloseReason.REPLACED) }
        return result
    }

    // ---- closing -----------------------------------------------------------------------------

    fun close(
        gui: GuiImpl,
        player: Player,
        reason: GuiCloseReason,
    ): Boolean {
        val session = gui.viewerSessions[player.uniqueId] ?: return false
        return closeSession(session, reason, notifyClient = true)
    }

    /**
     * Removes [session]; returns `false` if it was already gone (closing is idempotent, and exactly one caller
     * wins the race and fires the close event).
     */
    fun closeSession(
        session: ViewerSession,
        reason: GuiCloseReason,
        notifyClient: Boolean,
    ): Boolean {
        if (!sessions.remove(session.player.uniqueId, session)) return false
        if (notifyClient) {
            try {
                session.lock.withLock { protocol.close(session.player, session.containerId) }
            } catch (t: Throwable) {
                logger.warn("Could not send the close packet to {}: {}", session.player.username, t.toString())
            }
        }
        retire(session, reason)
        return true
    }

    private fun retire(
        session: ViewerSession,
        reason: GuiCloseReason,
    ) {
        session.closed = true
        session.gui.viewerSessions.remove(session.player.uniqueId, session)
        session.serial.execute {
            val event = GuiCloseEvent(session.player, session.gui, session.platform, reason)
            events.dispatch(event)
            session.gui.fireClose(event)
        }
    }

    // ---- refreshing --------------------------------------------------------------------------

    /** Re-renders [session] and sends only what changed (or re-opens the window if the title changed). */
    fun refresh(session: ViewerSession) {
        synchronized(session.refreshLock) {
            if (session.closed) return
            val items = session.gui.render(session.player, session.platform, session.state)
            val title = session.gui.title
            session.lock.withLock {
                if (session.closed) return
                try {
                    if (title != session.title) {
                        session.title = title
                        session.rendered = items
                        protocol.open(session.player, WindowSpec(session.containerId, session.nextStateId(), session.gui.layout, title, items))
                        return
                    }
                    val changes = diff(session.rendered, items)
                    if (changes.isEmpty()) return
                    session.rendered = items
                    if (changes.size > FULL_RESYNC_THRESHOLD) {
                        protocol.resync(session.player, spec(session))
                    } else {
                        protocol.updateSlots(session.player, session.containerId, session.nextStateId(), changes)
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to refresh GUI {} for {}", session.gui.id, session.player.username, t)
                }
            }
        }
    }

    /** Re-renders a single slot of [session]. */
    fun refreshSlot(
        session: ViewerSession,
        slot: Int,
    ) {
        synchronized(session.refreshLock) {
            if (session.closed) return
            val items = session.gui.render(session.player, session.platform, session.state)
            session.lock.withLock {
                if (session.closed) return
                val next = items[slot]
                val previous = session.rendered[slot]
                if (previous == null && next == null || previous != null && next != null && previous.looksLike(next)) return
                session.rendered = session.rendered.toMutableList().also { it[slot] = next }
                try {
                    protocol.updateSlots(session.player, session.containerId, session.nextStateId(), mapOf(slot to next))
                } catch (t: Throwable) {
                    logger.error("Failed to refresh slot {} for {}", slot, session.player.username, t)
                }
            }
        }
    }

    private fun diff(
        old: List<GuiItem?>,
        new: List<GuiItem?>,
    ): Map<Int, GuiItem?> {
        val changes = linkedMapOf<Int, GuiItem?>()
        for (slot in new.indices) {
            val before = old.getOrNull(slot)
            val after = new[slot]
            val same = (before == null && after == null) || (before != null && after != null && before.looksLike(after))
            if (!same) changes[slot] = after
        }
        return changes
    }

    private fun spec(session: ViewerSession): WindowSpec =
        WindowSpec(session.containerId, session.nextStateId(), session.gui.layout, session.title, session.rendered)

    // ---- inbound traffic ---------------------------------------------------------------------

    override fun onClick(
        player: UUID,
        click: RawClick,
    ): PacketVerdict {
        val session = sessions[player]
        if (session == null || session.containerId != click.containerId) {
            // A click for a window we already closed (in flight when it closed) must not reach the backend either.
            return if (ContainerIds.isReserved(click.containerId)) PacketVerdict.CONSUME else PacketVerdict.PASS
        }
        val step = ClickInterpreter.interpret(click, session.gui.size)

        // Undo the client's prediction right away, on this thread — except mid-drag, where the client is only
        // highlighting slots and a re-sync would cancel the drag.
        if (step !is ClickStep.DragStart && step !is ClickStep.DragAdd) {
            try {
                session.lock.withLock {
                    if (!session.closed) protocol.resync(session.player, spec(session))
                }
            } catch (t: Throwable) {
                logger.warn("Could not re-sync {}: {}", session.player.username, t.toString())
            }
        }
        session.serial.execute { process(session, step, click) }
        return PacketVerdict.CONSUME
    }

    override fun onClose(
        player: UUID,
        containerId: Int,
    ): PacketVerdict {
        val session = sessions[player]
        if (session != null && session.containerId == containerId) {
            closeSession(session, GuiCloseReason.CLIENT, notifyClient = false)
            return PacketVerdict.CONSUME
        }
        return if (ContainerIds.isReserved(containerId)) PacketVerdict.CONSUME else PacketVerdict.PASS
    }

    override fun onBackendContainerOpened(player: UUID) {
        sessions[player]?.let { closeSession(it, GuiCloseReason.BACKEND_CONTAINER, notifyClient = false) }
    }

    /** Runs on the session's serial executor. */
    private fun process(
        session: ViewerSession,
        step: ClickStep,
        click: RawClick,
    ) {
        if (session.closed) return
        when (step) {
            is ClickStep.Click -> {
                handleClick(session, step, click.cursor)
            }

            is ClickStep.DragStart -> {
                session.drag.start(step.type)
            }

            is ClickStep.DragAdd -> {
                session.drag.add(step.slot)
            }

            ClickStep.DragEnd -> {
                val drag = session.drag.end() ?: return
                val event =
                    GuiDragEvent(
                        session.player,
                        session.gui,
                        session.platform,
                        drag.type,
                        drag.guiSlots,
                        drag.inventorySlots,
                        click.cursor,
                    )
                events.dispatch(event)
                session.gui.fireDrag(event)
            }

            ClickStep.Unknown -> {
                session.drag.reset()
            }
        }
    }

    private fun handleClick(
        session: ViewerSession,
        step: ClickStep.Click,
        cursor: CursorItem?,
    ) {
        val item = if (step.area == SlotArea.GUI) session.rendered.getOrNull(step.slot) else null
        val event =
            GuiClickEvent(
                session.player,
                session.gui,
                session.platform,
                step.slot,
                step.area,
                item,
                step.type,
                step.hotbarSlot,
                cursor,
            )
        events.dispatch(event)
        session.gui.fireClick(event)
        if (event.cancelled || item == null) return

        val context = ClickContextImpl(event, actions)
        for ((types, handler) in item.handlers) {
            if (step.type !in types) continue
            try {
                handler.handle(context)
            } catch (t: Throwable) {
                logger.error("A click handler of GUI {} slot {} threw", session.gui.id, step.slot, t)
            }
        }
    }

    // ---- lifecycle events from the proxy -------------------------------------------------------

    /** The player left the proxy: drop everything we know about them. */
    fun onDisconnect(player: UUID) {
        sessions[player]?.let { closeSession(it, GuiCloseReason.DISCONNECT, notifyClient = false) }
        protocol.forget(player)
        platforms.forget(player)
        actions.forget(player)
    }

    /**
     * The player connected to another backend. The client discards its screens when the world changes,
     * so the session is dropped without sending anything (a packet in the middle of a phase change would be
     * a protocol error).
     */
    fun onServerSwitch(player: UUID) {
        sessions[player]?.let { closeSession(it, GuiCloseReason.SERVER_SWITCH, notifyClient = false) }
    }

    /** Closes every GUI (plugin disable / proxy shutdown) and detaches from the protocol layer. */
    fun shutdown() {
        sessions.values.toList().forEach { closeSession(it, GuiCloseReason.SHUTDOWN, notifyClient = true) }
        protocol.unbind()
    }

    private companion object {
        /** Above this many changed slots a full content packet is cheaper than one packet per slot. */
        const val FULL_RESYNC_THRESHOLD = 8
    }
}
