package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import io.mockk.every
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.event.CursorItem
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiClickEvent
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.common.actions.GuiActionExecutor
import nl.klrnbk.minecraft.plugins.gui.common.events.GuiEventBus
import nl.klrnbk.minecraft.plugins.gui.common.platform.CachingPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.protocol.GuiProtocol
import nl.klrnbk.minecraft.plugins.gui.common.protocol.InboundHandler
import nl.klrnbk.minecraft.plugins.gui.common.protocol.PacketVerdict
import nl.klrnbk.minecraft.plugins.gui.common.protocol.ProtocolCheck
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClick
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode
import nl.klrnbk.minecraft.plugins.gui.common.protocol.WindowSpec
import org.slf4j.helpers.NOPLogger
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** A mocked Velocity player. */
fun testPlayer(
    name: String = "Steve",
    uuid: UUID = UUID.randomUUID(),
): Player =
    mockk(relaxed = true) {
        every { uniqueId } returns uuid
        every { username } returns name
        every { isActive } returns true
    }

/** A [GuiProtocol] that records what the framework asks of it and lets tests play the client. */
class FakeGuiProtocol : GuiProtocol {
    sealed interface Op {
        class Open(
            val player: Player,
            val window: WindowSpec,
        ) : Op

        class Resync(
            val player: Player,
            val window: WindowSpec,
        ) : Op

        class Update(
            val player: Player,
            val containerId: Int,
            val stateId: Int,
            val changes: Map<Int, GuiItem?>,
        ) : Op

        class Close(
            val player: Player,
            val containerId: Int,
        ) : Op
    }

    val ops = CopyOnWriteArrayList<Op>()
    val forgotten = CopyOnWriteArrayList<UUID>()

    @Volatile var checkResult = ProtocolCheck.OK

    @Volatile var failOpen = false
    lateinit var handler: InboundHandler
    var unbound = false

    override val description = "fake"

    override fun check(player: Player) = checkResult

    override fun open(
        player: Player,
        window: WindowSpec,
    ) {
        if (failOpen) error("boom")
        ops += Op.Open(player, window)
    }

    override fun resync(
        player: Player,
        window: WindowSpec,
    ) {
        ops += Op.Resync(player, window)
    }

    override fun updateSlots(
        player: Player,
        containerId: Int,
        stateId: Int,
        changes: Map<Int, GuiItem?>,
    ) {
        ops += Op.Update(player, containerId, stateId, changes)
    }

    override fun close(
        player: Player,
        containerId: Int,
    ) {
        ops += Op.Close(player, containerId)
    }

    override fun forget(player: UUID) {
        forgotten += player
    }

    override fun bind(handler: InboundHandler) {
        this.handler = handler
    }

    override fun unbind() {
        unbound = true
    }

    inline fun <reified T : Op> opsOf(): List<T> = ops.filterIsInstance<T>()

    /** Simulates the client sending a container click. */
    fun click(
        player: Player,
        containerId: Int,
        slot: Int,
        button: Int = 0,
        mode: RawClickMode = RawClickMode.PICKUP,
        cursor: CursorItem? = null,
    ): PacketVerdict = handler.onClick(player.uniqueId, RawClick(containerId, 0, slot, button, mode, cursor))
}

class RecordingActionExecutor : GuiActionExecutor {
    val executed = CopyOnWriteArrayList<Pair<GuiAction, Player>>()
    val events = CopyOnWriteArrayList<GuiClickEvent?>()
    val connected = CopyOnWriteArrayList<Player>()

    override fun execute(
        action: GuiAction,
        player: Player,
        event: GuiClickEvent?,
    ) {
        executed += action to player
        events += event
    }

    override fun onServerConnected(player: Player) {
        connected += player
    }

    override fun forget(player: UUID) = Unit
}

/** A fully wired [GuiManager] whose event handlers run synchronously on the calling thread, for deterministic tests. */
class GuiTestHarness(
    executor: Executor = Executor { it.run() },
    platforms: (Player) -> GuiPlatform = { GuiPlatform.JAVA },
    /** Supply a real executor to run actions for real; by default they are only recorded in [actions]. */
    actionsFactory: ((GuiManager) -> GuiActionExecutor)? = null,
) {
    val protocol = FakeGuiProtocol()
    val actions = RecordingActionExecutor()
    val bus = GuiEventBus(NOPLogger.NOP_LOGGER)
    val manager =
        GuiManager(protocol, CachingPlatformDetector { platforms(it) }, bus, executor, NOPLogger.NOP_LOGGER) { manager ->
            actionsFactory?.invoke(manager) ?: actions
        }
    val api = GuiApiImpl(manager, NOPLogger.NOP_LOGGER)
}
