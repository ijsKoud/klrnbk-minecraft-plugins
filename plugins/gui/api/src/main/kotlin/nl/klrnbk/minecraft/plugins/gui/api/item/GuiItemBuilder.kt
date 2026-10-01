package nl.klrnbk.minecraft.plugins.gui.api.item

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction

/** DSL marker so `onClick { ... }` receivers cannot accidentally reach an outer builder. */
@DslMarker
public annotation class GuiDsl

/**
 * Mutable builder behind [guiItem]. Not thread-safe; only the resulting [GuiItem] is.
 */
@GuiDsl
public class GuiItemBuilder {
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()

    /** Required. */
    public var material: Material? = null

    /** Stack size, 1..99 (Minecraft's absolute maximum). */
    public var amount: Int = 1
        set(value) {
            require(value in 1..MAX_AMOUNT) { "amount must be in 1..$MAX_AMOUNT, got $value" }
            field = value
        }

    /** The display name as a [Component]. */
    public var nameComponent: Component? = null

    /** The display name as MiniMessage, e.g. `"<aqua>Spawn"`. Reads back the plain-text-free component form only when set through here. */
    public var name: String?
        get() = nameComponent?.let { miniMessage.serialize(it) }
        set(value) {
            nameComponent = value?.let { miniMessage.deserialize(it) }
        }

    private var loreLines: List<Component> = emptyList()

    /** Sets the tooltip lines from MiniMessage strings (an empty string is a blank line). */
    public fun lore(vararg lines: String) {
        lore(lines.asList())
    }

    public fun lore(lines: List<String>) {
        loreLines = lines.map { miniMessage.deserialize(it) }
    }

    /** Sets the tooltip lines from ready-made components. */
    public fun loreComponents(lines: List<Component>) {
        loreLines = lines.toList()
    }

    /** Appends one MiniMessage line to the lore. */
    public fun addLore(line: String) {
        loreLines = loreLines + miniMessage.deserialize(line)
    }

    /** Forces the enchantment glint on (`true`), off (`false`), or leaves it to the item (`null`). */
    public var glint: Boolean? = null

    /** Shorthand for `glint = true`. */
    public fun glow() {
        glint = true
    }

    private val enchantments = linkedMapOf<Key, Int>()

    /** Adds an enchantment by id (`"minecraft:sharpness"` or `"sharpness"`). Level 1..255. */
    public fun enchant(
        id: String,
        level: Int = 1,
    ) {
        require(level in 1..255) { "enchantment level must be in 1..255, got $level" }
        enchantments[keyOf(id)] = level
    }

    /** The legacy single-number `custom_model_data` (a resource-pack float index). */
    public var customModelData: Int? = null

    private var customModelStrings: List<String> = emptyList()

    /** The string entries of the modern `custom_model_data` component. */
    public fun customModelStrings(vararg values: String) {
        customModelStrings = values.asList()
    }

    private var itemModel: Key? = null

    /** Sets the `item_model` component (a resource-pack model id such as `"klrnbk:menu/spawn"`). */
    public fun itemModel(id: String) {
        itemModel = keyOf(id)
    }

    /** Hides the whole tooltip (name, lore and all): the item shows nothing on hover. */
    public var hideTooltip: Boolean = false

    private val hiddenComponents = linkedSetOf<Key>()

    /** Hides individual tooltip sections by component id, e.g. `hideTooltipComponents("minecraft:enchantments")`. */
    public fun hideTooltipComponents(vararg ids: String) {
        ids.mapTo(hiddenComponents, ::keyOf)
    }

    private val components = linkedMapOf<Key, GuiData>()

    /**
     * Sets any data component by id. Prefer the typed properties above; this is the escape hatch
     * for components added by newer Minecraft versions. See [GuiData].
     */
    public fun component(
        id: String,
        value: GuiData,
    ) {
        components[keyOf(id)] = value
    }

    private val handlers = mutableListOf<Pair<Set<GuiClickType>, GuiClickHandler>>()
    private val overrides = mutableMapOf<GuiPlatform, GuiItemBuilder.() -> Unit>()

    internal val platformOverrides: MutableMap<GuiPlatform, GuiItemBuilder.() -> Unit> get() = overrides

    /** Runs [handler] for left/right clicks, plain or with shift ([GuiClickType.PRIMARY]). */
    public fun onClick(handler: GuiClickContext.() -> Unit) {
        handlers += GuiClickType.PRIMARY to GuiClickHandler { it.handler() }
    }

    /** Runs [handler] only for the given click types (any [GuiClickType], not just the primary ones), e.g. `onClick(GuiClickType.LEFT, GuiClickType.SHIFT_LEFT) { ... }`. */
    public fun onClick(
        vararg types: GuiClickType,
        handler: GuiClickContext.() -> Unit,
    ) {
        require(types.isNotEmpty()) { "pass at least one click type, or use onClick { } for the primary clicks" }
        handlers += types.toSet() to GuiClickHandler { it.handler() }
    }

    /** Runs [handler] for left clicks (plain and shift). */
    public fun onLeftClick(handler: GuiClickContext.() -> Unit): Unit = onClick(GuiClickType.LEFT, GuiClickType.SHIFT_LEFT, handler = handler)

    /** Runs [handler] for right clicks (plain and shift). */
    public fun onRightClick(handler: GuiClickContext.() -> Unit): Unit = onClick(GuiClickType.RIGHT, GuiClickType.SHIFT_RIGHT, handler = handler)

    /** Executes a declarative [GuiAction] on every click. */
    public fun action(action: GuiAction) {
        onClick { execute(action) }
    }

    /**
     * Customises the item for one platform, e.g. drop a `custom_model_data` that Bedrock cannot show:
     * `forPlatform(GuiPlatform.BEDROCK) { customModelData = null }`.
     */
    public fun forPlatform(
        platform: GuiPlatform,
        override: GuiItemBuilder.() -> Unit,
    ) {
        overrides[platform] = override
    }

    /** Creates the immutable item. @throws IllegalStateException if [material] is unset. */
    public fun build(): GuiItem =
        GuiItem(
            material = checkNotNull(material) { "guiItem { } needs a material" },
            amount = amount,
            name = nameComponent,
            lore = loreLines.toList(),
            glint = glint,
            enchantments = enchantments.toMap(),
            customModelData = customModelData,
            customModelStrings = customModelStrings.toList(),
            itemModel = itemModel,
            hideTooltip = hideTooltip,
            hiddenTooltipComponents = hiddenComponents.toSet(),
            components = components.toMap(),
            handlers = handlers.toList(),
            platformOverrides = overrides.toMap(),
        )

    internal companion object {
        const val MAX_AMOUNT: Int = 99

        fun keyOf(id: String): Key = if (':' in id) Key.key(id) else Key.key(Key.MINECRAFT_NAMESPACE, id)

        fun from(item: GuiItem): GuiItemBuilder =
            GuiItemBuilder().also { b ->
                b.material = item.material
                b.amount = item.amount
                b.nameComponent = item.name
                b.loreLines = item.lore
                b.glint = item.glint
                b.enchantments.putAll(item.enchantments)
                b.customModelData = item.customModelData
                b.customModelStrings = item.customModelStrings
                b.itemModel = item.itemModel
                b.hideTooltip = item.hideTooltip
                b.hiddenComponents.addAll(item.hiddenTooltipComponents)
                b.components.putAll(item.components)
                b.handlers.addAll(item.handlers)
                b.overrides.putAll(item.platformOverrides)
            }
    }
}
