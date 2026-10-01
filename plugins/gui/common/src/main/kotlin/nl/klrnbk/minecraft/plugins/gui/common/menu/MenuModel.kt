package nl.klrnbk.minecraft.plugins.gui.common.menu

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiData

/** Builds a [GuiAction] for one click, given a function that substitutes `{placeholders}` in text. */
fun interface ActionTemplate {
    fun build(substitute: (String) -> String): GuiAction
}

/**
 * The parsed form of an `items:` entry. Every field is optional so the same shape serves as a platform override
 * (`platform: bedrock: { ... }`); only the base item must have a [material].
 */
class ItemSpec(
    val material: String? = null,
    val amount: Int? = null,
    val name: String? = null,
    val lore: List<String>? = null,
    val glow: Boolean? = null,
    val enchantments: Map<String, Int>? = null,
    /** `null` = not specified; a value of 0 or below in an override removes the base value. */
    val customModelData: Int? = null,
    val customModelStrings: List<String>? = null,
    val itemModel: String? = null,
    val hideTooltip: Boolean? = null,
    val hideTooltipComponents: List<String>? = null,
    val components: Map<String, GuiData>? = null,
    val platformOverrides: Map<GuiPlatform, ItemSpec> = emptyMap(),
    val clicks: List<Pair<Set<GuiClickType>, List<ActionTemplate>>> = emptyList(),
) {
    /** True if name or lore mention a `{placeholder}`, so the item must be rendered per viewer. */
    val isDynamic: Boolean
        get() = (listOfNotNull(name) + lore.orEmpty()).any { Placeholders.containsPlaceholder(it) }
}

class ItemDef(
    val id: String,
    val slots: List<Int>,
    val spec: ItemSpec,
)

class MenuDef(
    val id: String,
    val title: String,
    val layout: GuiLayout,
    val commands: List<String>,
    val permission: String?,
    val refreshSeconds: Int?,
    val filler: ItemSpec?,
    val items: List<ItemDef>,
    /** Menu ids referenced by `open_menu` actions, for the post-load consistency check. */
    val referencedMenus: Set<String>,
)

/** A problem found while reading a menu file. */
class MenuIssue(
    val file: String,
    val path: String,
    val message: String,
) {
    override fun toString(): String = "$file: ${if (path.isEmpty()) "" else "$path: "}$message"
}
