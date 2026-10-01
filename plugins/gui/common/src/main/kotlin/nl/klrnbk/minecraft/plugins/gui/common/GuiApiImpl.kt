package nl.klrnbk.minecraft.plugins.gui.common

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiApi
import nl.klrnbk.minecraft.plugins.gui.api.GuiCloseReason
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiMenus
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.MenuReloadReport
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.event.GuiEvents
import org.slf4j.Logger

class GuiApiImpl(
    private val manager: GuiManager,
    private val logger: Logger,
    menusFactory: (GuiApi) -> GuiMenus = { NoMenus },
) : GuiApi {
    private val miniMessage = MiniMessage.miniMessage()

    override fun create(
        title: String,
        rows: Int,
    ): Gui = create(miniMessage.deserialize(title), GuiLayout.chest(rows))

    override fun create(
        title: Component,
        rows: Int,
    ): Gui = create(title, GuiLayout.chest(rows))

    override fun create(
        title: String,
        layout: GuiLayout,
    ): Gui = create(miniMessage.deserialize(title), layout)

    override fun create(
        title: Component,
        layout: GuiLayout,
    ): Gui = GuiImpl(manager, layout, title, logger)

    override fun platformOf(player: Player): GuiPlatform = manager.platformOf(player)

    override fun currentGui(player: Player): Gui? = manager.currentSession(player)?.gui

    override fun closeGui(player: Player): Boolean {
        val session = manager.currentSession(player) ?: return false
        return manager.closeSession(session, GuiCloseReason.PLUGIN, notifyClient = true)
    }

    override val menus: GuiMenus by lazy { menusFactory(this) }

    override val events: GuiEvents get() = manager.events

    override val protocolDescription: String get() = manager.protocolDescription
}

/** Used when no menu folder is configured (tests, embedding): there simply are no YAML menus. */
object NoMenus : GuiMenus {
    override val ids: Set<String> = emptySet()

    override fun get(id: String): Gui? = null

    override fun open(
        id: String,
        player: Player,
    ): GuiOpenResult = GuiOpenResult.UNKNOWN_MENU

    override fun reload(): MenuReloadReport = MenuReloadReport(emptyList(), emptyList())
}
