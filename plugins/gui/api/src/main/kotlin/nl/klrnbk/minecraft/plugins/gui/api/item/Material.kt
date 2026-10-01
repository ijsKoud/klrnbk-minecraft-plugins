package nl.klrnbk.minecraft.plugins.gui.api.item

import net.kyori.adventure.key.Key

/**
 * An item type, identified by its namespaced id (`minecraft:diamond`).
 *
 * This is deliberately *not* an enum: Minecraft adds items every release, and an enum
 * baked into the API would need an API release for each one. The id is validated
 * against the client's item registry by the protocol layer when the item is rendered
 * (an unknown id is displayed as a barrier and logged), so new items work as soon as the
 * protocol layer knows them. The constants below are conveniences for common GUI items.
 */
public class Material private constructor(
    public val key: Key,
) {
    override fun equals(other: Any?): Boolean = other is Material && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = key.asString()

    public companion object {
        /** Creates a material from `"minecraft:diamond"` or just `"diamond"` (namespace defaults to `minecraft`). */
        public fun of(id: String): Material = of(if (':' in id) Key.key(id) else Key.key(Key.MINECRAFT_NAMESPACE, id))

        public fun of(key: Key): Material = Material(key)

        private fun vanilla(path: String) = Material(Key.key(Key.MINECRAFT_NAMESPACE, path))

        public val STONE: Material = vanilla("stone")
        public val GRASS_BLOCK: Material = vanilla("grass_block")
        public val DIAMOND: Material = vanilla("diamond")
        public val EMERALD: Material = vanilla("emerald")
        public val GOLD_INGOT: Material = vanilla("gold_ingot")
        public val IRON_INGOT: Material = vanilla("iron_ingot")
        public val NETHER_STAR: Material = vanilla("nether_star")
        public val ENDER_PEARL: Material = vanilla("ender_pearl")
        public val COMPASS: Material = vanilla("compass")
        public val CLOCK: Material = vanilla("clock")
        public val PAPER: Material = vanilla("paper")
        public val BOOK: Material = vanilla("book")
        public val WRITABLE_BOOK: Material = vanilla("writable_book")
        public val NAME_TAG: Material = vanilla("name_tag")
        public val CHEST: Material = vanilla("chest")
        public val ENDER_CHEST: Material = vanilla("ender_chest")
        public val BARRIER: Material = vanilla("barrier")
        public val ARROW: Material = vanilla("arrow")
        public val SPECTRAL_ARROW: Material = vanilla("spectral_arrow")
        public val PLAYER_HEAD: Material = vanilla("player_head")
        public val DIAMOND_SWORD: Material = vanilla("diamond_sword")
        public val TNT: Material = vanilla("tnt")
        public val REDSTONE: Material = vanilla("redstone")
        public val LIME_DYE: Material = vanilla("lime_dye")
        public val RED_DYE: Material = vanilla("red_dye")
        public val GRAY_DYE: Material = vanilla("gray_dye")
        public val OAK_SIGN: Material = vanilla("oak_sign")
        public val LIGHT: Material = vanilla("light")
        public val WHITE_STAINED_GLASS_PANE: Material = vanilla("white_stained_glass_pane")
        public val GRAY_STAINED_GLASS_PANE: Material = vanilla("gray_stained_glass_pane")
        public val BLACK_STAINED_GLASS_PANE: Material = vanilla("black_stained_glass_pane")
        public val RED_STAINED_GLASS_PANE: Material = vanilla("red_stained_glass_pane")
        public val LIME_STAINED_GLASS_PANE: Material = vanilla("lime_stained_glass_pane")
        public val BLUE_STAINED_GLASS_PANE: Material = vanilla("blue_stained_glass_pane")
    }
}
