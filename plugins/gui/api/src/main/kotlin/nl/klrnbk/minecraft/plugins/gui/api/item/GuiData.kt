package nl.klrnbk.minecraft.plugins.gui.api.item

/**
 * A version-independent, protocol-free description of a data component value — the
 * same shape as the component's JSON/NBT form (`{count: 3, ...}`, `[1, 2]`, `"text"`).
 *
 * It is the escape hatch for components without a dedicated builder method
 * (`GuiItemBuilder.component`). The protocol layer converts it into the component's wire
 * format for the *player's* client version.
 *
 * **Which components work is decided by the protocol layer, not by this API.** Minecraft's component
 * codecs are not generically constructible from a tree, so the layer keeps an explicit list of supported ids
 * (currently `custom_data`, `item_name`, `max_stack_size`, `max_damage`, `damage`, `repair_cost`, `unbreakable`,
 * `rarity` and `tooltip_style`). An unsupported id — or a value of the wrong shape — is skipped with a one-time
 * warning in the log; the rest of the item is still shown. Use the typed builder properties for everything else
 * (name, lore, enchantments, model data, tooltip, ...).
 */
public sealed interface GuiData {
    public class Text(
        public val value: String,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is Text && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "\"$value\""
    }

    public class Bool(
        public val value: Boolean,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is Bool && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value.toString()
    }

    public class Integer(
        public val value: Int,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is Integer && other.value == value

        override fun hashCode(): Int = value

        override fun toString(): String = value.toString()
    }

    public class Decimal(
        public val value: Double,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is Decimal && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value.toString()
    }

    public class ListOf(
        public val values: List<GuiData>,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is ListOf && other.values == values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = values.toString()
    }

    public class Compound(
        public val values: Map<String, GuiData>,
    ) : GuiData {
        override fun equals(other: Any?): Boolean = other is Compound && other.values == values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = values.toString()
    }

    public companion object {
        public fun text(value: String): GuiData = Text(value)

        public fun bool(value: Boolean): GuiData = Bool(value)

        public fun int(value: Int): GuiData = Integer(value)

        public fun double(value: Double): GuiData = Decimal(value)

        public fun list(vararg values: GuiData): GuiData = ListOf(values.toList())

        public fun compound(vararg entries: Pair<String, GuiData>): GuiData = Compound(entries.toMap())
    }
}
