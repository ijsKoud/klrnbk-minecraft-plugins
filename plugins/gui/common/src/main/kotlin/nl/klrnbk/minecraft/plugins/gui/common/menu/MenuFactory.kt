package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.gui.api.Gui
import nl.klrnbk.minecraft.plugins.gui.api.GuiApi
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItemBuilder
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import org.slf4j.Logger

/** Turns a parsed [MenuDef] into a live [Gui] built with the public API — YAML menus get no privileges plugin code doesn't have. */
class MenuFactory(
    private val api: GuiApi,
    private val server: ProxyServer,
    private val logger: Logger,
    private val external: ExternalPlaceholders = ExternalPlaceholders.NONE,
) {
    fun build(
        def: MenuDef,
        fileName: String,
        issues: MutableList<MenuIssue>,
    ): Gui {
        val gui = api.create(def.title, def.layout)
        for (item in def.items) {
            try {
                if (item.spec.isDynamic || needsPerViewer(item.spec)) {
                    // Rendered per viewer, on every refresh, so {online}, %papi% and <miniplaceholders> are always current.
                    item.slots.forEach { slot ->
                        gui.setItem(slot) { ctx -> render(item.spec, Placeholders.substitutor(server, ctx.player, ctx.platform), ctx.player) }
                    }
                } else {
                    val rendered = render(item.spec, { it }, null)
                    item.slots.forEach { gui.setItem(it, rendered) }
                }
            } catch (e: Exception) {
                issues += MenuIssue(fileName, "items.${item.id}", "could not be built: ${e.message}")
            }
        }
        def.filler?.let {
            try {
                gui.fillEmpty(render(it, { text -> text }, null))
            } catch (e: Exception) {
                issues += MenuIssue(fileName, "filler", "could not be built: ${e.message}")
            }
        }
        return gui
    }

    private fun needsPerViewer(spec: ItemSpec): Boolean = (listOfNotNull(spec.name) + spec.lore.orEmpty()).any(external::needsPerViewer)

    private fun render(
        spec: ItemSpec,
        substitute: (String) -> String,
        viewer: Player?,
    ): GuiItem =
        guiItem {
            applyFields(this, spec, substitute, viewer)
            for ((types, templates) in spec.clicks) {
                onClick(*types.toTypedArray()) {
                    val substitutor = Placeholders.substitutor(server, player, event.platform)
                    for (template in templates) {
                        try {
                            execute(template.build(substitutor))
                        } catch (e: IllegalArgumentException) {
                            logger.warn("A menu action for {} was invalid after placeholder substitution: {}", player.username, e.message)
                        }
                    }
                }
            }
            for ((platform, override) in spec.platformOverrides) {
                forPlatform(platform) { applyFields(this, override, substitute, viewer) }
            }
        }

    private fun applyFields(
        b: GuiItemBuilder,
        spec: ItemSpec,
        substitute: (String) -> String,
        viewer: Player?,
    ) {
        spec.material?.let { b.material = Material.of(it) }
        spec.amount?.let { b.amount = it }
        spec.name?.let { b.nameComponent = external.render(substitute(it), viewer) }
        spec.lore?.let { lines -> b.loreComponents(lines.map { external.render(substitute(it), viewer) }) }
        spec.glow?.let { b.glint = it }
        spec.enchantments?.forEach { (id, level) -> b.enchant(id, level) }
        spec.customModelData?.let { b.customModelData = it.takeIf { v -> v > 0 } }
        spec.customModelStrings?.let { b.customModelStrings(*it.toTypedArray()) }
        spec.itemModel?.let { b.itemModel(it) }
        spec.hideTooltip?.let { b.hideTooltip = it }
        spec.hideTooltipComponents?.let { b.hideTooltipComponents(*it.toTypedArray()) }
        spec.components?.forEach { (id, data) -> b.component(id, data) }
    }

}
