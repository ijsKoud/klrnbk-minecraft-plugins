package nl.klrnbk.minecraft.plugins.gui.velocity.facades

import com.github.retrooper.packetevents.PacketEvents
import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.GuiApi
import nl.klrnbk.minecraft.plugins.gui.api.GuiProvider
import nl.klrnbk.minecraft.plugins.gui.common.GuiApiImpl
import nl.klrnbk.minecraft.plugins.gui.common.GuiManager
import nl.klrnbk.minecraft.plugins.gui.common.actions.ProxyActionExecutor
import nl.klrnbk.minecraft.plugins.gui.common.events.GuiEventBus
import nl.klrnbk.minecraft.plugins.gui.common.platform.CachingPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.platform.ChainedPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.platform.FloodgatePlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.platform.FloodgateUuidPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.platform.GeyserPlatformDetector
import nl.klrnbk.minecraft.plugins.gui.common.protocol.GuiProtocol
import nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents.PacketEventsGuiProtocol
import nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents.PacketEventsTransport
import nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents.PeItemConverter
import nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents.PlayerInventoryMirror
import nl.klrnbk.minecraft.plugins.gui.common.menu.ExternalPlaceholders
import nl.klrnbk.minecraft.plugins.gui.common.menu.MenuService
import nl.klrnbk.minecraft.plugins.gui.common.menu.MiniPlaceholdersBridge
import nl.klrnbk.minecraft.plugins.gui.common.menu.PapiPlaceholders
import nl.klrnbk.minecraft.plugins.gui.common.menu.PapiProxyBridgeFormatter
import nl.klrnbk.minecraft.plugins.gui.velocity.commands.MenuCommands
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Wires the framework together and owns its lifecycle. */
@Singleton
class PluginFacade
    @Inject
    constructor(
        private val server: ProxyServer,
        private val logger: Logger,
        @DataDirectory private val dataDirectory: Path,
    ) {
        private var manager: GuiManager? = null
        private var commands: MenuCommands? = null
        private var external: ExternalPlaceholders? = null
        private var executor: ExecutorService? = null

        fun start(plugin: Any) {
            val api = PacketEvents.getAPI()
            check(api != null && api.isInitialized) {
                "PacketEvents is not initialised. Install the PacketEvents *Velocity* plugin (packetevents-velocity-<version>.jar from " +
                    "github.com/retrooper/packetevents/releases) in the proxy's plugins folder."
            }
            val protocol: GuiProtocol =
                PacketEventsGuiProtocol(PacketEventsTransport(), PeItemConverter(logger), PlayerInventoryMirror(), logger)
            val platforms =
                CachingPlatformDetector(
                    ChainedPlatformDetector(listOf(FloodgatePlatformDetector(), GeyserPlatformDetector(), FloodgateUuidPlatformDetector())),
                )
            // Virtual threads: handlers may block briefly (database lookups...) without pinning a platform thread per player.
            val pool = Executors.newVirtualThreadPerTaskExecutor()
            executor = pool
            val created = GuiManager(protocol, platforms, GuiEventBus(logger), pool, logger) { ProxyActionExecutor(server, it, logger) }
            manager = created

            lateinit var guiApi: GuiApi
            // Optional integrations: found by class name at runtime (Velocity lets us see the classes of optional dependencies).
            val mini = MiniPlaceholdersBridge().takeIf { it.available }
            val papiFormatter = PapiProxyBridgeFormatter().takeIf { it.available }
            val papi = papiFormatter?.let { PapiPlaceholders(it, logger, onChanged = { player -> guiApi.currentGui(player)?.refresh(player) }) }
            val placeholders = ExternalPlaceholders(mini, papi)
            external = placeholders
            logger.info("Menu placeholders: built-in {player} etc.; MiniPlaceholders {}; PAPIProxyBridge {}", if (mini != null) "found" else "not installed", if (papi != null) "found" else "not installed")
            val menuService = MenuService(dataDirectory.resolve("menus"), server, logger, api = { guiApi }, external = placeholders)
            guiApi = GuiApiImpl(created, logger) { menuService }
            GuiProvider.register(guiApi)

            // Menus are loaded after the API is registered so YAML menus and plugins see the same, ready framework.
            // A broken menu file must never stop the framework from starting: problems are logged and skipped.
            val menuCommands = MenuCommands(server, plugin, menuService, logger)
            commands = menuCommands
            menuCommands.registerAdminCommand()
            try {
                menuService.reload()
                menuCommands.sync()
            } catch (e: Exception) {
                logger.error("Could not load the YAML menus", e)
            }
            logger.info("GUI framework ready. Protocol: {}", guiApi.protocolDescription)
        }

        fun stop() {
            GuiProvider.unregister()
            commands?.shutdown()
            commands = null
            manager?.shutdown()
            manager = null
            executor?.let {
                it.shutdown()
                if (!it.awaitTermination(2, TimeUnit.SECONDS)) it.shutdownNow()
            }
            executor = null
        }

        fun onDisconnect(player: Player) {
            manager?.onDisconnect(player.uniqueId)
            external?.forget(player.uniqueId)
        }

        fun onServerSwitch(player: Player) {
            manager?.onServerSwitch(player.uniqueId)
        }

        fun onServerPostConnect(player: Player) {
            manager?.actions?.onServerConnected(player)
        }
    }
