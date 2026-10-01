package nl.klrnbk.minecraft.plugins.gui.common.menu

import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuParserTest {
    private val parser = MenuParser(openMenu = { _, _ -> })

    private fun parse(yaml: String) = parser.parse("test", "test.yml", yaml.trimIndent())

    private fun ParseResult.problems() = issues.map { it.toString() }

    private fun ParseResult.actionsOf(item: String, click: Set<GuiClickType> = GuiClickType.PRIMARY): List<GuiAction> =
        menu!!.items.first { it.id == item }.spec.clicks.first { it.first == click }.second.map { it.build { s -> s } }

    @Test
    fun `a complete menu parses`() {
        val r =
            parse(
                """
                title: "<gold>Shop"
                rows: 3
                commands: [Shop, /buy]
                permission: shop.use
                refresh_seconds: 5
                filler: {material: gray_stained_glass_pane, name: " "}
                items:
                  spawn:
                    slot: 13
                    material: minecraft:diamond
                    amount: 2
                    name: "<aqua>Spawn"
                    lore: ["<gray>a", ""]
                    glow: true
                    enchantments: {sharpness: 5}
                    custom_model_data: 4
                    item_model: klrnbk:x
                    hide_tooltip_components: [attribute_modifiers]
                    components: {"minecraft:max_stack_size": 16}
                """,
            )
        assertEquals(emptyList<String>(), r.problems())
        val menu = r.menu!!
        assertEquals("<gold>Shop", menu.title)
        assertEquals(GuiLayout.chest(3), menu.layout)
        assertEquals(listOf("shop", "buy"), menu.commands)
        assertEquals("shop.use", menu.permission)
        assertEquals(5, menu.refreshSeconds)
        assertNotNull(menu.filler)
        val spec = menu.items.single().spec
        assertEquals(listOf(13), menu.items.single().slots)
        assertEquals("minecraft:diamond", spec.material)
        assertEquals(2, spec.amount)
        assertEquals(listOf("<gray>a", ""), spec.lore)
        assertEquals(mapOf("sharpness" to 5), spec.enchantments)
        assertEquals(4, spec.customModelData)
        assertEquals(listOf("attribute_modifiers"), spec.hideTooltipComponents)
        assertEquals(1, spec.components!!.size)
    }

    @Test
    fun `hopper and dispenser types`() {
        assertEquals(GuiLayout.Hopper, parse("title: x\ntype: hopper").menu!!.layout)
        assertEquals(GuiLayout.Dispenser, parse("title: x\ntype: Dispenser").menu!!.layout)
    }

    @Test
    fun `menu level problems drop the menu with a helpful message`() {
        assertTrue(parse("rows: 3").problems().any { "title: required" in it })
        assertNull(parse("rows: 3").menu)
        assertTrue(parse("title: x").problems().any { "rows: required" in it })
        assertTrue(parse("title: x\nrows: 7").problems().any { "rows" in it })
        assertNull(parse("title: x\nrows: 0").menu)
        assertTrue(parse("title: x\ntype: anvil").problems().any { "type" in it && "anvil" in it })
        assertTrue(parse("- a\n- b").problems().any { "mapping" in it })
        assertTrue(parse("title: [unclosed").problems().any { "not valid YAML" in it })
        assertNull(parse("").menu)
    }

    @Test
    fun `problems name the file and the yaml path`() {
        val text = parse("title: x\nrows: 1\nitems:\n  a:\n    slot: 0").problems().single()
        assertEquals("test.yml: items.a.material: required (e.g. diamond or minecraft:diamond)", text)
    }

    @Test
    fun `a bad item is skipped, the rest of the menu survives`() {
        val r = parse("title: x\nrows: 1\nitems:\n  bad: {slot: 0}\n  good: {slot: 1, material: stone}")
        assertEquals(1, r.issues.size)
        assertEquals(listOf("good"), r.menu!!.items.map { it.id })
    }

    @Test
    fun `slots accept numbers lists and ranges and are checked against the size`() {
        val r =
            parse(
                """
                title: x
                rows: 1
                items:
                  a: {slot: 3, material: stone}
                  b: {slots: [0, "5-6"], material: stone}
                  c: {slot: "7-8", material: stone}
                  d: {slot: 9, material: stone}
                  e: {slot: abc, material: stone}
                  f: {material: stone}
                """,
            )
        val slots = r.menu!!.items.associate { it.id to it.slots }
        assertEquals(listOf(3), slots["a"])
        assertEquals(listOf(0, 5, 6), slots["b"])
        assertEquals(listOf(7, 8), slots["c"])
        assertNull(slots["d"], "no valid slot: skipped")
        assertNull(slots["e"])
        assertNull(slots["f"])
        assertEquals(3, r.issues.size)
        assertTrue(r.problems().any { "slot 9 is outside 0..8" in it })
        assertTrue(r.problems().any { "'abc'" in it })
        assertTrue(r.problems().any { "needs a 'slot'" in it })
    }

    @Test
    fun `field validation`() {
        val r =
            parse(
                """
                title: x
                rows: 1
                commands: ["ok", "Bad Name!"]
                refresh_seconds: 0
                items:
                  a: {slot: 0, material: "Not Valid!"}
                  b: {slot: 1, material: stone, amount: 100}
                  c: {slot: 2, material: stone, enchantments: {sharpness: 0}}
                  d: {slot: 3, material: stone, platform: {switch: {name: x}}}
                """,
            )
        val p = r.problems()
        assertTrue(p.any { "commands" in it && "bad name!" in it })
        assertTrue(p.any { "refresh_seconds" in it })
        assertTrue(p.any { "items.a.material" in it })
        assertTrue(p.any { "items.b.amount" in it })
        assertTrue(p.any { "items.c.enchantments.sharpness" in it })
        assertTrue(p.any { "items.d.platform.switch" in it })
        assertEquals(listOf("ok"), r.menu!!.commands)
    }

    @Test
    fun `default actions are the primary clicks and on-blocks are per click type`() {
        val r =
            parse(
                """
                title: x
                rows: 1
                items:
                  a:
                    slot: 0
                    material: stone
                    actions: [close, refresh, {server_command: spawn}]
                    on:
                      drop: [{player_command: "/help"}]
                      shift_left: [{connect: lobby}]
                """,
            )
        assertEquals(emptyList<String>(), r.problems())
        assertEquals(listOf(GuiAction.Close, GuiAction.Refresh, GuiAction.ExecuteServerCommand("spawn")), r.actionsOf("a"))
        assertEquals(listOf<GuiAction>(GuiAction.ExecutePlayerCommand("help")), r.actionsOf("a", setOf(GuiClickType.DROP)))
        assertEquals(listOf<GuiAction>(GuiAction.ConnectToServer("lobby")), r.actionsOf("a", setOf(GuiClickType.SHIFT_LEFT)))
    }

    @Test
    fun `every action kind`() {
        val r =
            parse(
                """
                title: x
                rows: 1
                items:
                  a:
                    slot: 0
                    material: stone
                    actions:
                      - server_command: {command: "warp home", server: survival}
                      - proxy_command: glist
                      - proxy_command: {command: "send {player} lobby", as: console}
                      - message: "<green>hi {player}"
                      - plugin_message: {channel: "klrnbk:gui", text: "go"}
                      - clickable_message: {text: "[a]", suggest_command: warp}
                      - clickable_message: {text: "[b]", run_command: warp}
                      - clickable_message: {text: "[c]", open_url: "https://klrnbk.nl"}
                      - clickable_message: {text: "[d]", copy_to_clipboard: "x"}
                """,
            )
        assertEquals(emptyList<String>(), r.problems())
        val a = r.actionsOf("a")
        assertEquals(GuiAction.ExecuteServerCommand("warp home", "survival"), a[0])
        assertEquals(GuiAction.ExecuteProxyCommand("glist"), a[1])
        assertEquals(GuiAction.ExecuteProxyCommand("send {player} lobby", ProxyCommandExecutor.CONSOLE), a[2])
        assertEquals(GuiAction.SendMessage("<green>hi {player}"), a[3])
        assertEquals(GuiAction.SendPluginMessage("klrnbk:gui", "go".toByteArray()), a[4])
        assertEquals(GuiAction.SendClickableMessage("[a]", ClientClick.SuggestCommand("warp")), a[5])
        assertTrue((a[6] as GuiAction.SendClickableMessage).click is ClientClick.RunCommand)
        assertTrue((a[7] as GuiAction.SendClickableMessage).click is ClientClick.OpenUrl)
        assertTrue((a[8] as GuiAction.SendClickableMessage).click is ClientClick.CopyToClipboard)
    }

    @Test
    fun `invalid actions are reported at load time, not at click time`() {
        val r =
            parse(
                """
                title: x
                rows: 1
                items:
                  a:
                    slot: 0
                    material: stone
                    actions:
                      - server_command: "   "
                      - teleport: spawn
                      - {server_command: a, player_command: b}
                      - explode
                      - proxy_command: {command: x, as: root}
                      - plugin_message: {channel: nocolon}
                      - clickable_message: {text: "[x]"}
                      - clickable_message: {text: "[x]", open_url: "javascript:alert(1)"}
                      - connect: ""
                      - close
                    on:
                      hover: [close]
                """,
            )
        assertEquals(10, r.issues.size, r.problems().toString())
        assertEquals(listOf<GuiAction>(GuiAction.Close), r.actionsOf("a"), "the one valid action still works")
        assertTrue(r.problems().any { "items.a.actions[1]" in it && "teleport" in it })
        assertTrue(r.problems().any { "items.a.on.hover" in it })
    }

    @Test
    fun `placeholders in commands are validated with neutral text`() {
        val r = parse("title: x\nrows: 1\nitems:\n  a: {slot: 0, material: stone, actions: [{player_command: \"msg {player} hi\"}, {player_command: \"{player}\"}]}")
        assertEquals(emptyList<String>(), r.problems())
    }

    @Test
    fun `open_menu references are collected`() {
        val r = parse("title: x\nrows: 1\nitems:\n  a: {slot: 0, material: stone, actions: [{open_menu: shop}]}")
        assertEquals(setOf("shop"), r.menu!!.referencedMenus)
    }

    @Test
    fun `platform overrides parse into partial specs`() {
        val r = parse("title: x\nrows: 1\nitems:\n  a: {slot: 0, material: stone, custom_model_data: 5, platform: {bedrock: {custom_model_data: 0, name: b}}}")
        val bedrock = r.menu!!.items.single().spec.platformOverrides[GuiPlatform.BEDROCK]!!
        assertNull(bedrock.material)
        assertEquals(0, bedrock.customModelData)
        assertEquals("b", bedrock.name)
    }

    @Test
    fun `items with placeholders in name or lore are dynamic`() {
        val r = parse("title: x\nrows: 1\nitems:\n  a: {slot: 0, material: stone, name: 'Hi {player}'}\n  b: {slot: 1, material: stone, name: 'Hi', actions: [{message: '{player}'}]}")
        assertTrue(r.menu!!.items[0].spec.isDynamic)
        assertFalse(r.menu!!.items[1].spec.isDynamic, "placeholders in actions are resolved at click time")
    }
}
