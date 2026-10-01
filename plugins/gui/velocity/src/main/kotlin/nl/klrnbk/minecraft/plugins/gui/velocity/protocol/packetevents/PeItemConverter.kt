package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.component.ComponentType
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemEnchantments
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemRarity
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemTooltipStyle
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemUnbreakable
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemTooltipDisplay
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentType
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes
import com.github.retrooper.packetevents.protocol.item.type.ItemType
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.nbt.NBT
import com.github.retrooper.packetevents.protocol.nbt.NBTByte
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound
import com.github.retrooper.packetevents.protocol.nbt.NBTDouble
import com.github.retrooper.packetevents.protocol.nbt.NBTInt
import com.github.retrooper.packetevents.protocol.nbt.NBTList
import com.github.retrooper.packetevents.protocol.nbt.NBTString
import com.github.retrooper.packetevents.protocol.nbt.NBTType
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.resources.ResourceLocation
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiData
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns the protocol-free [GuiItem] into a PacketEvents [ItemStack] for one client version.
 *
 * Since 1.20.5 items are a registry id plus *data components* (`custom_name`, `lore`, `enchantments`, ...),
 * not NBT — this class builds components directly and never touches the legacy `display`/`Lore` NBT layout.
 * PacketEvents writes them in the wire format of the receiving client, which is what keeps a single
 * `GuiItem` valid across client versions.
 *
 * Anything the client version cannot represent (an item or component id it does not know) degrades
 * gracefully: the problem is logged once and the rest of the item is still shown.
 */
internal class PeItemConverter(
    private val logger: Logger,
) {
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun convert(
        item: GuiItem,
        version: ClientVersion,
    ): ItemStack {
        val id = item.material.key.asString()
        val type = resolveType(id, version)
        val stack = ItemStack.builder().type(type ?: ItemTypes.BARRIER).amount(item.amount).build()

        if (type == null) {
            warnOnce("item:$id", "Unknown item '$id' for client ${version.name}; showing a barrier instead")
            stack.setComponent(ComponentTypes.CUSTOM_NAME, Component.text("Unknown item: $id").noItalics())
        }

        item.name?.let { stack.setComponent(ComponentTypes.CUSTOM_NAME, it.noItalics()) }
        if (item.lore.isNotEmpty()) {
            stack.setComponent(ComponentTypes.LORE, ItemLore(item.lore.map { it.noItalics() }))
        }
        item.glint?.let { stack.setComponent(ComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, it) }

        if (item.enchantments.isNotEmpty()) {
            val resolved = linkedMapOf<EnchantmentType, Int>()
            for ((key, level) in item.enchantments) {
                val enchantment = EnchantmentTypes.getByName(key.asString())
                if (enchantment == null) {
                    warnOnce("enchantment:$key", "Unknown enchantment '$key'; skipped")
                } else {
                    resolved[enchantment] = level
                }
            }
            if (resolved.isNotEmpty()) stack.setComponent(ComponentTypes.ENCHANTMENTS, ItemEnchantments(resolved))
        }

        applyCustomModelData(stack, item, version)
        item.itemModel?.let { stack.setComponent(ComponentTypes.ITEM_MODEL, ItemModel(ResourceLocation(it))) }

        if (item.hideTooltip || item.hiddenTooltipComponents.isNotEmpty()) {
            val hidden = linkedSetOf<ComponentType<*>>()
            for (key in item.hiddenTooltipComponents) {
                val component = ComponentTypes.getByName(key.asString())
                if (component == null) warnOnce("component:$key", "Unknown component '$key' in hidden tooltip components; skipped") else hidden += component
            }
            stack.setComponent(ComponentTypes.TOOLTIP_DISPLAY, ItemTooltipDisplay(item.hideTooltip, hidden))
        }

        for ((key, data) in item.components) applyRawComponent(stack, key, data)
        return stack
    }

    private fun resolveType(
        id: String,
        version: ClientVersion,
    ): ItemType? {
        val type = ItemTypes.getByName(id) ?: return null
        return if (type.getId(version) >= 0) type else null
    }

    private fun applyCustomModelData(
        stack: ItemStack,
        item: GuiItem,
        version: ClientVersion,
    ) {
        if (item.customModelData == null && item.customModelStrings.isEmpty()) return
        // 1.21.4 turned the single int into lists of floats / flags / strings / colours.
        if (version.isNewerThanOrEquals(ClientVersion.V_1_21_4)) {
            val floats = listOfNotNull(item.customModelData?.toFloat())
            stack.setComponent(
                ComponentTypes.CUSTOM_MODEL_DATA_LISTS,
                ItemCustomModelData(floats, emptyList(), item.customModelStrings, emptyList()),
            )
        } else {
            @Suppress("DEPRECATION") // the single-int component only exists on pre-1.21.4 clients
            item.customModelData?.let { stack.setComponent(ComponentTypes.CUSTOM_MODEL_DATA, it) }
        }
    }

    /**
     * PacketEvents' component types are wire-format codecs; most of them cannot be built from a generic tree (their NBT
     * `decode` is unsupported), so raw components go through this explicit table instead. Supporting another component id
     * is one entry here — see IMPLEMENTATION.md. Ids not listed are skipped with a one-time warning rather than silently
     * pretending to work.
     */
    private val rawComponents: Map<String, (ItemStack, GuiData) -> Unit> =
        mapOf(
            "minecraft:custom_data" to { stack, data -> stack.setComponent(ComponentTypes.CUSTOM_DATA, toNbt(data) as? NBTCompound ?: bad("a compound")) },
            "minecraft:item_name" to { stack, data -> stack.setComponent(ComponentTypes.ITEM_NAME, MiniMessage.miniMessage().deserialize(text(data)).noItalics()) },
            "minecraft:max_stack_size" to { stack, data -> stack.setComponent(ComponentTypes.MAX_STACK_SIZE, int(data, 1..99)) },
            "minecraft:max_damage" to { stack, data -> stack.setComponent(ComponentTypes.MAX_DAMAGE, int(data, 1..Int.MAX_VALUE)) },
            "minecraft:damage" to { stack, data -> stack.setComponent(ComponentTypes.DAMAGE, int(data, 0..Int.MAX_VALUE)) },
            "minecraft:repair_cost" to { stack, data -> stack.setComponent(ComponentTypes.REPAIR_COST, int(data, 0..Int.MAX_VALUE)) },
            "minecraft:unbreakable" to { stack, data ->
                // Since 1.21.5 the component is an empty marker: present = unbreakable, absent = normal.
                if ((data as? GuiData.Bool)?.value ?: bad("a boolean")) stack.setComponent(ComponentTypes.UNBREAKABLE_MODERN, ItemUnbreakable(true))
            },
            "minecraft:rarity" to { stack, data ->
                val rarity = ItemRarity.entries.firstOrNull { it.name.equals(text(data), ignoreCase = true) } ?: bad("common, uncommon, rare or epic")
                stack.setComponent(ComponentTypes.RARITY, rarity)
            },
            "minecraft:tooltip_style" to { stack, data -> stack.setComponent(ComponentTypes.TOOLTIP_STYLE, ItemTooltipStyle(ResourceLocation(text(data)))) },
        )

    private fun applyRawComponent(
        stack: ItemStack,
        key: Key,
        data: GuiData,
    ) {
        val apply = rawComponents[key.asString()]
        if (apply == null) {
            warnOnce("component:$key", "The data component '$key' is not supported by the PacketEvents protocol layer; skipped")
            return
        }
        try {
            apply(stack, data)
        } catch (e: IllegalArgumentException) {
            warnOnce("component:$key:value", "The value $data does not fit data component '$key' (${e.message}); skipped")
        }
    }

    private fun bad(expected: String): Nothing = throw IllegalArgumentException("expected $expected")

    private fun text(data: GuiData): String = (data as? GuiData.Text)?.value ?: bad("text")

    private fun int(
        data: GuiData,
        range: IntRange,
    ): Int {
        val value = (data as? GuiData.Integer)?.value ?: bad("an integer")
        if (value !in range) bad("an integer in $range")
        return value
    }

    private fun warnOnce(
        key: String,
        message: String,
    ) {
        if (warned.add(key)) logger.warn(message)
    }

    private fun Component.noItalics(): Component = decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    companion object {
        /** Converts the protocol-free [GuiData] tree to PacketEvents' NBT model. */
        fun toNbt(data: GuiData): NBT =
            when (data) {
                is GuiData.Text -> {
                    NBTString(data.value)
                }

                is GuiData.Bool -> {
                    NBTByte(data.value)
                }

                is GuiData.Integer -> {
                    NBTInt(data.value)
                }

                is GuiData.Decimal -> {
                    NBTDouble(data.value)
                }

                is GuiData.Compound -> {
                    NBTCompound().also { compound -> data.values.forEach { (name, value) -> compound.setTag(name, toNbt(value)) } }
                }

                is GuiData.ListOf -> {
                    val children = data.values.map(::toNbt)
                    val type = if (children.isEmpty()) NBTType.STRING else NBTList.getCommonTagType(children)

                    @Suppress("UNCHECKED_CAST")
                    val list = NBTList(type as NBTType<NBT>)
                    children.forEach(list::addTagUnsafe)
                    list
                }
            }
    }
}
