package nl.klrnbk.minecraft.plugins.gui.api

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiData
import nl.klrnbk.minecraft.plugins.gui.api.item.Material
import nl.klrnbk.minecraft.plugins.gui.api.item.guiItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GuiItemTest {
    private val mm = MiniMessage.miniMessage()

    @Test
    fun `material is required`() {
        assertThrows<IllegalStateException> { guiItem { name = "x" } }
    }

    @Test
    fun `defaults`() {
        val item = guiItem { material = Material.STONE }
        assertEquals(1, item.amount)
        assertNull(item.name)
        assertTrue(item.lore.isEmpty())
        assertNull(item.glint)
        assertTrue(item.enchantments.isEmpty())
        assertNull(item.customModelData)
        assertFalse(item.hideTooltip)
        assertTrue(item.handlers.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 64, 99])
    fun `amount accepts 1 to 99`(amount: Int) {
        assertEquals(amount, guiItem { material = Material.STONE; this.amount = amount }.amount)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 100, 1000])
    fun `amount outside 1 to 99 is rejected`(amount: Int) {
        assertThrows<IllegalArgumentException> { guiItem { material = Material.STONE; this.amount = amount } }
    }

    @Test
    fun `name is parsed as MiniMessage`() {
        val item = guiItem { material = Material.DIAMOND; name = "<aqua>Spawn" }
        assertEquals(mm.deserialize("<aqua>Spawn"), item.name)
    }

    @Test
    fun `name can also be set as a component`() {
        val component = Component.text("Hi")
        assertSame(component, guiItem { material = Material.DIAMOND; nameComponent = component }.name)
    }

    @Test
    fun `lore lines are parsed and blank lines are kept`() {
        val item =
            guiItem {
                material = Material.DIAMOND
                lore("<gray>Teleport to spawn", "", "<yellow>Click to teleport")
            }
        assertEquals(3, item.lore.size)
        assertEquals(mm.deserialize("<gray>Teleport to spawn"), item.lore[0])
        assertEquals(Component.empty(), item.lore[1])
    }

    @Test
    fun `addLore appends and lore replaces`() {
        val item =
            guiItem {
                material = Material.DIAMOND
                lore("a", "b")
                addLore("c")
            }
        assertEquals(3, item.lore.size)
        val replaced = item.toBuilder().apply { lore("only") }.build()
        assertEquals(1, replaced.lore.size)
    }

    @Test
    fun `enchantments glint and model data are stored`() {
        val item =
            guiItem {
                material = Material.DIAMOND_SWORD
                enchant("sharpness", 5)
                enchant("minecraft:unbreaking")
                glow()
                customModelData = 7
                customModelStrings("a", "b")
                itemModel("klrnbk:menu/spawn")
                hideTooltipComponents("attribute_modifiers")
                hideTooltip = false
            }
        assertEquals(5, item.enchantments[net.kyori.adventure.key.Key.key("minecraft:sharpness")])
        assertEquals(1, item.enchantments[net.kyori.adventure.key.Key.key("minecraft:unbreaking")])
        assertEquals(true, item.glint)
        assertEquals(7, item.customModelData)
        assertEquals(listOf("a", "b"), item.customModelStrings)
        assertEquals("klrnbk:menu/spawn", item.itemModel.toString())
        assertEquals(setOf(net.kyori.adventure.key.Key.key("minecraft:attribute_modifiers")), item.hiddenTooltipComponents)
    }

    @Test
    fun `enchantment level is validated`() {
        assertThrows<IllegalArgumentException> { guiItem { material = Material.STONE; enchant("sharpness", 0) } }
        assertThrows<IllegalArgumentException> { guiItem { material = Material.STONE; enchant("sharpness", 256) } }
    }

    @Test
    fun `raw components are stored by key`() {
        val item =
            guiItem {
                material = Material.STONE
                component("minecraft:max_stack_size", GuiData.int(16))
            }
        assertEquals(GuiData.int(16), item.components[net.kyori.adventure.key.Key.key("minecraft:max_stack_size")])
    }

    @Test
    fun `an item is unaffected by later builder changes`() {
        val builder = net.kyori.adventure.key.Key.key("a:b").let { nl.klrnbk.minecraft.plugins.gui.api.item.GuiItemBuilder() }
        builder.material = Material.STONE
        builder.lore("one")
        val first = builder.build()
        builder.lore("two")
        assertEquals(1, first.lore.size)
        assertEquals("one", mm.serialize(first.lore.single()))
    }

    @Test
    fun `toBuilder round trips and derives variants`() {
        val original = guiItem { material = Material.DIAMOND; name = "<red>x"; lore("l"); enchant("sharpness", 1); amount = 3 }
        val copy = original.toBuilder().build()
        assertTrue(original.looksLike(copy))
        val variant = original.toBuilder().apply { amount = 4 }.build()
        assertFalse(original.looksLike(variant))
    }

    @Test
    fun `looksLike ignores click handlers`() {
        val a = guiItem { material = Material.STONE }
        val b = guiItem { material = Material.STONE; onClick { } }
        assertTrue(a.looksLike(b))
        assertEquals(1, b.handlers.size)
    }

    @Test
    fun `onClick registers handlers for the primary clicks and typed filters are kept`() {
        val item =
            guiItem {
                material = Material.STONE
                onClick { }
                onClick(GuiClickType.DROP, GuiClickType.NUMBER_KEY) { }
                onLeftClick { }
                onRightClick { }
            }
        assertEquals(4, item.handlers.size)
        assertEquals(GuiClickType.PRIMARY, item.handlers[0].first)
        assertEquals(setOf(GuiClickType.DROP, GuiClickType.NUMBER_KEY), item.handlers[1].first)
        assertEquals(setOf(GuiClickType.LEFT, GuiClickType.SHIFT_LEFT), item.handlers[2].first)
    }

    @Test
    fun `onClick with no types is rejected`() {
        assertThrows<IllegalArgumentException> { guiItem { material = Material.STONE; onClick(*emptyArray<GuiClickType>()) { } } }
    }

    @Test
    fun `platform overrides only apply to their platform`() {
        val item =
            guiItem {
                material = Material.DIAMOND
                customModelData = 5
                name = "<aqua>Hi"
                forPlatform(GuiPlatform.BEDROCK) {
                    customModelData = null
                    name = "<green>Bedrock"
                }
            }
        val java = item.forPlatform(GuiPlatform.JAVA)
        val bedrock = item.forPlatform(GuiPlatform.BEDROCK)
        assertSame(item, java)
        assertEquals(5, java.customModelData)
        assertNull(bedrock.customModelData)
        assertEquals(mm.deserialize("<green>Bedrock"), bedrock.name)
        // the resolved variant has no overrides left, so resolving twice is stable
        assertSame(bedrock, bedrock.forPlatform(GuiPlatform.BEDROCK))
    }
}
