package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.protocol.component.ComponentType
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemRarity
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.nbt.NBTString
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiData
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

class PeItemConverterTest {
    init {
        PeTestSupport.install()
    }

    private val warnings = mutableListOf<String>()
    private val logger: Logger =
        object : Logger by NOPLogger.NOP_LOGGER {
            override fun warn(msg: String) {
                warnings += msg
            }
        }
    private val converter = PeItemConverter(logger)
    private val v = ClientVersion.V_26_2
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `material and amount`() {
        val stack = converter.convert(guiItem { material = Material.DIAMOND; amount = 12 }, v)
        assertEquals(ItemTypes.DIAMOND, stack.type)
        assertEquals(12, stack.amount)
    }

    @Test
    fun `display name is a custom_name component with italics off by default`() {
        val stack = converter.convert(guiItem { material = Material.DIAMOND; name = "<aqua>Spawn" }, v)
        val name = stack.getComponent(ComponentTypes.CUSTOM_NAME).get()
        assertEquals("Spawn", plain.serialize(name))
        assertEquals(NamedTextColor.AQUA, name.color())
        assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC))
    }

    @Test
    fun `an explicit italic request is respected`() {
        val stack = converter.convert(guiItem { material = Material.PAPER; name = "<italic>Fancy" }, v)
        assertEquals(TextDecoration.State.TRUE, stack.getComponent(ComponentTypes.CUSTOM_NAME).get().decoration(TextDecoration.ITALIC))
    }

    @Test
    fun `lore lines keep their order and formatting`() {
        val stack = converter.convert(guiItem { material = Material.PAPER; lore("<gray>Online players: <white>42", "", "<yellow>Click") }, v)
        val lines = stack.getComponent(ComponentTypes.LORE).get().lines
        assertEquals(listOf("Online players: 42", "", "Click"), lines.map(plain::serialize))
        assertTrue(lines.all { it.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE })
        assertEquals(NamedTextColor.YELLOW, lines[2].color())
    }

    /** True when the stack carries an explicit override for [type] (as opposed to the item type's default value). */
    @Suppress("DEPRECATION") // no non-deprecated accessor for the override map in PacketEvents 2.14.0
    private fun ItemStack.patched(type: ComponentType<*>): Boolean = components.patches[type]?.isPresent == true

    @Test
    fun `a plain item sends no component overrides at all`() {
        val stack = converter.convert(guiItem { material = Material.STONE }, v)
        assertFalse(stack.hasComponentPatches(), "nothing but type and count goes over the wire")
    }

    @Test
    fun `only the requested components are overridden`() {
        val stack = converter.convert(guiItem { material = Material.STONE; name = "x" }, v)
        assertTrue(stack.patched(ComponentTypes.CUSTOM_NAME))
        assertFalse(stack.patched(ComponentTypes.LORE))
        assertFalse(stack.patched(ComponentTypes.ENCHANTMENTS))
        assertFalse(stack.patched(ComponentTypes.TOOLTIP_DISPLAY))
    }

    @Test
    fun `glint enchantments and item model`() {
        val stack =
            converter.convert(
                guiItem {
                    material = Material.DIAMOND_SWORD
                    glint = false
                    enchant("sharpness", 5)
                    itemModel("klrnbk:menu/spawn")
                },
                v,
            )
        assertEquals(false, stack.getComponent(ComponentTypes.ENCHANTMENT_GLINT_OVERRIDE).get())
        assertEquals(5, stack.getComponent(ComponentTypes.ENCHANTMENTS).get().getEnchantmentLevel(EnchantmentTypes.SHARPNESS))
        assertEquals("klrnbk:menu/spawn", stack.getComponent(ComponentTypes.ITEM_MODEL).get().modelLocation.toString())
    }

    @Test
    fun `custom model data uses the list form on modern clients and the integer on old ones`() {
        val item = guiItem { material = Material.DIAMOND; customModelData = 7; customModelStrings("gold") }
        val modern = converter.convert(item, ClientVersion.V_26_2).getComponent(ComponentTypes.CUSTOM_MODEL_DATA_LISTS).get()
        assertEquals(listOf(7f), modern.floats)
        assertEquals(listOf("gold"), modern.strings)
    }

    @Test
    fun `the tooltip can be hidden entirely or in parts`() {
        val hidden = converter.convert(guiItem { material = Material.DIAMOND_SWORD; hideTooltip = true }, v).getComponent(ComponentTypes.TOOLTIP_DISPLAY).get()
        assertTrue(hidden.isHideTooltip)

        val partial =
            converter
                .convert(guiItem { material = Material.DIAMOND_SWORD; hideTooltipComponents("attribute_modifiers", "enchantments") }, v)
                .getComponent(ComponentTypes.TOOLTIP_DISPLAY)
                .get()
        assertFalse(partial.isHideTooltip)
        assertEquals(setOf(ComponentTypes.ATTRIBUTE_MODIFIERS, ComponentTypes.ENCHANTMENTS), partial.hiddenComponents)
    }

    @Test
    fun `supported raw components are applied`() {
        val stack =
            converter.convert(
                guiItem {
                    material = Material.STONE
                    component("minecraft:max_stack_size", GuiData.int(16))
                    component("minecraft:unbreakable", GuiData.bool(true))
                    component("minecraft:rarity", GuiData.text("epic"))
                    component("minecraft:custom_data", GuiData.compound("gui" to GuiData.text("spawn")))
                    component("minecraft:item_name", GuiData.text("<gold>Named"))
                    component("minecraft:max_damage", GuiData.int(100))
                    component("minecraft:damage", GuiData.int(3))
                    component("minecraft:repair_cost", GuiData.int(2))
                    component("minecraft:tooltip_style", GuiData.text("klrnbk:fancy"))
                },
                v,
            )
        assertEquals(16, stack.getComponent(ComponentTypes.MAX_STACK_SIZE).get())
        assertTrue(stack.getComponent(ComponentTypes.UNBREAKABLE_MODERN).isPresent)
        assertEquals(ItemRarity.EPIC, stack.getComponent(ComponentTypes.RARITY).get())
        assertEquals("spawn", (stack.getComponent(ComponentTypes.CUSTOM_DATA).get().getTagOrNull("gui") as NBTString).value)
        assertEquals("Named", plain.serialize(stack.getComponent(ComponentTypes.ITEM_NAME).get()))
        assertEquals(100, stack.getComponent(ComponentTypes.MAX_DAMAGE).get())
        assertEquals(3, stack.getComponent(ComponentTypes.DAMAGE).get())
        assertEquals(2, stack.getComponent(ComponentTypes.REPAIR_COST).get())
        assertEquals("klrnbk:fancy", stack.getComponent(ComponentTypes.TOOLTIP_STYLE).get().tooltipLoc.toString())
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `unsupported raw components are skipped with a warning instead of pretending to work`() {
        val item = guiItem { material = Material.STONE; component("minecraft:food", GuiData.compound("nutrition" to GuiData.int(4))) }
        val stack = converter.convert(item, v)
        converter.convert(item, v)
        assertFalse(stack.hasComponentPatches())
        assertEquals(1, warnings.size, "reported once")
        assertTrue("food" in warnings.single())
    }

    @Test
    fun `raw values of the wrong shape are skipped`() {
        val stack =
            converter.convert(
                guiItem {
                    material = Material.STONE
                    component("minecraft:max_stack_size", GuiData.text("not a number"))
                    component("minecraft:rarity", GuiData.text("mythic"))
                    component("minecraft:max_damage", GuiData.int(0))
                    component("minecraft:custom_data", GuiData.int(1))
                },
                v,
            )
        assertFalse(stack.hasComponentPatches())
        assertEquals(4, warnings.size)
    }

    @Test
    fun `an unknown material becomes a labelled barrier and is reported once`() {
        val item = guiItem { material = Material.of("klrnbk:not_an_item") }
        val stack = converter.convert(item, v)
        converter.convert(item, v)
        assertEquals(ItemTypes.BARRIER, stack.type)
        assertTrue("not_an_item" in plain.serialize(stack.getComponent(ComponentTypes.CUSTOM_NAME).get()))
        assertEquals(1, warnings.count { "not_an_item" in it }, "logged once, not per render")
    }

    @Test
    fun `an unknown enchantment or component is skipped but the rest of the item survives`() {
        val stack =
            converter.convert(
                guiItem {
                    material = Material.DIAMOND
                    name = "<red>Still here"
                    enchant("klrnbk:made_up", 3)
                    component("klrnbk:made_up", GuiData.text("x"))
                },
                v,
            )
        assertEquals("Still here", plain.serialize(stack.getComponent(ComponentTypes.CUSTOM_NAME).get()))
        assertFalse(stack.patched(ComponentTypes.ENCHANTMENTS))
        assertEquals(2, warnings.size)
    }

    @Test
    fun `gui data converts to the matching nbt shapes`() {
        val nbt = PeItemConverter.toNbt(GuiData.compound("a" to GuiData.int(1), "b" to GuiData.list(GuiData.text("x"), GuiData.text("y")), "c" to GuiData.list()))
        assertEquals(3, (nbt as com.github.retrooper.packetevents.protocol.nbt.NBTCompound).tags.size)
    }

    @Test
    fun `conversion is repeatable`() {
        val item = guiItem { material = Material.DIAMOND; name = MiniMessage.miniMessage().serialize(Component.text("x")) }
        assertEquals(converter.convert(item, v), converter.convert(item, v))
    }
}
