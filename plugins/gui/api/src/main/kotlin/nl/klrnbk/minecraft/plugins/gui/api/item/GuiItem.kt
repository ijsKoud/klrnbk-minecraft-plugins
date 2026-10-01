package nl.klrnbk.minecraft.plugins.gui.api.item

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform

/**
 * An immutable description of one item in a GUI: what it looks like and what clicking it does.
 *
 * Build one with [guiItem]. Because instances are immutable they can be shared freely between
 * GUIs, slots and threads. The item is *not* a Minecraft `ItemStack` — it is turned into the
 * right wire representation for each player's client version by the protocol layer.
 *
 * Text is stored as Adventure [Component]s. Display names and lore are rendered *without*
 * the vanilla default italics unless the component asks for italics explicitly.
 */
public class GuiItem internal constructor(
    public val material: Material,
    public val amount: Int,
    public val name: Component?,
    public val lore: List<Component>,
    public val glint: Boolean?,
    public val enchantments: Map<Key, Int>,
    public val customModelData: Int?,
    public val customModelStrings: List<String>,
    public val itemModel: Key?,
    public val hideTooltip: Boolean,
    public val hiddenTooltipComponents: Set<Key>,
    public val components: Map<Key, GuiData>,
    public val handlers: List<Pair<Set<GuiClickType>, GuiClickHandler>>,
    internal val platformOverrides: Map<GuiPlatform, GuiItemBuilder.() -> Unit>,
) {
    /**
     * The variant of this item for [platform]: the item itself when it has no override, otherwise a
     * copy with the override applied (see `GuiItemBuilder.forPlatform`). The result has no further overrides.
     */
    public fun forPlatform(platform: GuiPlatform): GuiItem {
        val override = platformOverrides[platform] ?: return this
        return toBuilder().apply { platformOverrides.clear() }.apply(override).build()
    }

    /** A builder pre-filled with this item, for deriving variants. */
    public fun toBuilder(): GuiItemBuilder = GuiItemBuilder.from(this)

    /**
     * Visual equality: everything a player can see. Handlers are ignored — two items that look
     * identical produce identical packets and need not be re-sent.
     */
    public fun looksLike(other: GuiItem): Boolean =
        material == other.material &&
            amount == other.amount &&
            name == other.name &&
            lore == other.lore &&
            glint == other.glint &&
            enchantments == other.enchantments &&
            customModelData == other.customModelData &&
            customModelStrings == other.customModelStrings &&
            itemModel == other.itemModel &&
            hideTooltip == other.hideTooltip &&
            hiddenTooltipComponents == other.hiddenTooltipComponents &&
            components == other.components

    override fun toString(): String = "GuiItem($material x$amount, name=$name)"
}

/** Builds a [GuiItem]: `guiItem { material = Material.DIAMOND; name = "<aqua>Spawn" }`. */
public fun guiItem(block: GuiItemBuilder.() -> Unit): GuiItem = GuiItemBuilder().apply(block).build()
