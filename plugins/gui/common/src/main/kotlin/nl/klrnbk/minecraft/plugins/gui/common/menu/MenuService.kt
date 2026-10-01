package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiApi
import nl.klrnbk.minecraft.plugins.gui.api.GuiMenus
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.MenuReloadReport
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/** A menu that loaded successfully. */
class LoadedMenu(
    val definition: MenuDef,
    val gui: Gui,
)

/**
 * Loads `*.yml` / `*.yaml` menus from [directory] and serves them as [GuiMenus].
 *
 * The loaded set is one immutable map in an [AtomicReference]: readers (opening a menu, a click on `open_menu`) never
 * block, and a reload swaps the whole set at once, so nobody ever sees a half-loaded state. Reloads themselves are
 * serialised.
 */
class MenuService(
    private val directory: Path,
    server: ProxyServer,
    private val logger: Logger,
    private val api: () -> GuiApi,
    private val defaultMenuResource: String? = "menus/example.yml",
    private val external: ExternalPlaceholders = ExternalPlaceholders.NONE,
) : GuiMenus {
    private val loaded = AtomicReference<Map<String, LoadedMenu>>(emptyMap())
    private val parser = MenuParser(openMenu = { id, player -> open(id, player) })
    private val factory by lazy { MenuFactory(api(), server, logger, external) }
    private val reloadLock = Any()

    override val ids: Set<String> get() = loaded.get().keys

    override fun get(id: String): Gui? = loaded.get()[id]?.gui

    /** The loaded menus with their definitions (commands, permission, refresh interval) for the plugin wiring. */
    val menus: Map<String, LoadedMenu> get() = loaded.get()

    override fun open(
        id: String,
        player: Player,
    ): GuiOpenResult {
        val menu = loaded.get()[id] ?: return GuiOpenResult.UNKNOWN_MENU
        val permission = menu.definition.permission
        if (permission != null && !player.hasPermission(permission)) return GuiOpenResult.NO_PERMISSION
        return menu.gui.open(player)
    }

    override fun reload(): MenuReloadReport =
        synchronized(reloadLock) {
            val problems = mutableListOf<MenuIssue>()
            val result = linkedMapOf<String, LoadedMenu>()

            Files.createDirectories(directory)
            seedExample()

            val files =
                Files.list(directory).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.extension.lowercase() in setOf("yml", "yaml") }.sorted().toList()
                }
            for (file in files) {
                val id = file.nameWithoutExtension.lowercase()
                if (!ID.matches(id)) {
                    problems += MenuIssue(file.name, "", "the file name must be letters, digits, - or _ (it becomes the menu id)")
                    continue
                }
                if (id in result) {
                    problems += MenuIssue(file.name, "", "menu id '$id' is already defined by another file")
                    continue
                }
                val parsed =
                    try {
                        parser.parse(id, file.name, Files.readString(file))
                    } catch (e: Exception) {
                        problems += MenuIssue(file.name, "", "could not be read: ${e.message}")
                        continue
                    }
                problems += parsed.issues
                val definition = parsed.menu ?: continue
                result[id] = LoadedMenu(definition, factory.build(definition, file.name, problems))
            }

            for ((id, menu) in result) {
                menu.definition.referencedMenus.filter { it !in result }.forEach {
                    problems += MenuIssue("$id.yml", "", "open_menu refers to unknown menu '$it'")
                }
            }

            loaded.set(result)
            problems.forEach { logger.warn("Menu problem: {}", it) }
            logger.info("Loaded {} menu(s): {}", result.size, result.keys.joinToString().ifEmpty { "none" })
            MenuReloadReport(result.keys.toList(), problems.map { it.toString() })
        }

    /** Writes the bundled example on the very first start (an empty folder), so admins have something to copy. */
    private fun seedExample() {
        val resource = defaultMenuResource ?: return
        if (Files.list(directory).use { it.findAny().isPresent }) return
        javaClass.classLoader.getResourceAsStream(resource)?.use { Files.copy(it, directory.resolve(resource.substringAfterLast('/'))) }
    }

    private companion object {
        val ID = Regex("[a-z0-9_-]+")
    }
}
