package nl.klrnbk.minecraft.plugins.gui.api

import com.velocitypowered.api.proxy.Player

/** The outcome of loading the YAML menus. */
public class MenuReloadReport(
    /** Ids of the menus that loaded, in file-name order. */
    public val loaded: List<String>,
    /** Human-readable problems (`shop.yml: items.buy.material: unknown ...`). A problem skips the item or menu it names, never the whole folder. */
    public val problems: List<String>,
)

/**
 * Menus defined by YAML files (the `menus` folder of the plugin (`.yml` files)), so a server can add menus without any plugin code.
 * The file name (without extension) is the menu id. See the plugin README for the file format.
 *
 * A YAML menu is an ordinary [Gui]: [get] returns it, so plugins may open it, listen to its events or refresh it.
 * All members are thread-safe. Implemented only by the framework.
 */
public interface GuiMenus {
    public val ids: Set<String>

    /** The loaded menu, or `null`. After [reload] a new instance replaces it; viewers keep the old one until they close it. */
    public fun get(id: String): Gui?

    /** Opens menu [id] for [player], honouring the menu's `permission`. */
    public fun open(
        id: String,
        player: Player,
    ): GuiOpenResult

    /** Re-reads every file. Broken files are reported and skipped; the previously loaded version of a menu that now fails is dropped. */
    public fun reload(): MenuReloadReport
}
