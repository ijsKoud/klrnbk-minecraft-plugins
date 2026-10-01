package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.every
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.gui.api.GuiOpenResult
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.common.FakeGuiProtocol.Op
import nl.klrnbk.minecraft.plugins.gui.common.GuiTestHarness
import nl.klrnbk.minecraft.plugins.gui.common.protocol.RawClickMode
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

class MenuServiceTest {
    @TempDir
    lateinit var dir: Path

    private val server = mockk<ProxyServer>(relaxed = true) { every { playerCount } returns 7 }
    private var platform = GuiPlatform.JAVA
    private val harness by lazy { GuiTestHarness(platforms = { platform }) }
    private val menus by lazy { MenuService(dir.resolve("menus"), server, NOPLogger.NOP_LOGGER, api = { harness.api }) }

    private fun write(name: String, yaml: String) {
        Files.createDirectories(dir.resolve("menus"))
        Files.writeString(dir.resolve("menus").resolve(name), yaml.trimIndent())
    }

    private fun window(): List<nl.klrnbk.minecraft.plugins.gui.api.item.GuiItem?> = harness.protocol.opsOf<Op.Open>().last().window.items

    @Test
    fun `an empty folder is seeded with the bundled example and it loads cleanly`() {
        val report = menus.reload()
        assertTrue(Files.exists(dir.resolve("menus/example.yml")))
        assertEquals(listOf("example"), report.loaded)
        assertEquals(emptyList<String>(), report.problems)
        assertEquals(setOf("example"), menus.ids)
        assertNotNull(menus.get("example"))
    }

    @Test
    fun `an existing folder is never seeded`() {
        write("shop.yml", "title: x\nrows: 1")
        assertEquals(listOf("shop"), menus.reload().loaded)
        assertTrue(Files.notExists(dir.resolve("menus/example.yml")))
    }

    @Test
    fun `menus render into real guis`() {
        write("shop.yml", "title: '<gold>Shop'\nrows: 2\nfiller: {material: gray_stained_glass_pane}\nitems:\n  a: {slots: [0, 1], material: diamond, name: '<aqua>A'}")
        menus.reload()
        val player = testPlayer()
        assertEquals(GuiOpenResult.OPENED, menus.open("shop", player))
        val items = window()
        assertEquals(18, items.size)
        assertEquals("minecraft:diamond", items[0]!!.material.toString())
        assertEquals("minecraft:diamond", items[1]!!.material.toString())
        assertEquals("minecraft:gray_stained_glass_pane", items[2]!!.material.toString(), "filler fills the rest")
    }

    @Test
    fun `later items win over earlier ones on the same slot and filler never overwrites`() {
        write("m.yml", "title: x\nrows: 1\nfiller: {material: dirt}\nitems:\n  a: {slot: 0, material: stone}\n  b: {slot: 0, material: diamond}")
        menus.reload()
        menus.open("m", testPlayer())
        assertEquals("minecraft:diamond", window()[0]!!.material.toString())
    }

    @Test
    fun `clicking runs the actions with placeholders resolved for the clicker`() {
        write(
            "m.yml",
            """
            title: x
            rows: 1
            items:
              a:
                slot: 0
                material: stone
                actions:
                  - server_command: "give {player} apple"
                  - message: "{platform} on {online}"
                on:
                  drop: [{player_command: "drop {uuid}"}]
            """,
        )
        menus.reload()
        val player = testPlayer("Alex")
        menus.open("m", player)
        val id = harness.protocol.opsOf<Op.Open>().last().window.containerId
        harness.protocol.click(player, id, 0)
        harness.protocol.click(player, id, 0, 0, RawClickMode.THROW)
        assertEquals(
            listOf<GuiAction>(
                GuiAction.ExecuteServerCommand("give Alex apple"),
                GuiAction.SendMessage("java on 7"),
                GuiAction.ExecutePlayerCommand("drop ${player.uniqueId}"),
            ),
            harness.actions.executed.map { it.first },
        )
    }

    @Test
    fun `items with placeholders are dynamic and follow the viewer`() {
        write("m.yml", "title: x\nrows: 1\nitems:\n  a: {slot: 0, material: paper, name: 'Hi {player}', lore: ['{online} online']}")
        menus.reload()
        val pl = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        menus.open("m", testPlayer("Alex"))
        assertEquals("Hi Alex", pl.serialize(window()[0]!!.name!!))
        assertEquals("7 online", pl.serialize(window()[0]!!.lore.single()))
    }

    @Test
    fun `platform overrides apply per platform`() {
        write("m.yml", "title: x\nrows: 1\nitems:\n  a: {slot: 0, material: nether_star, custom_model_data: 5, platform: {bedrock: {custom_model_data: 0}}}")
        menus.reload()
        menus.open("m", testPlayer())
        assertEquals(5, window()[0]!!.customModelData)
        platform = GuiPlatform.BEDROCK
        menus.open("m", testPlayer())
        assertNull(window()[0]!!.customModelData)
    }

    @Test
    fun `permission is checked and unknown menus reported`() {
        write("m.yml", "title: x\nrows: 1\npermission: gui.m")
        menus.reload()
        val denied = testPlayer().also { every { it.hasPermission("gui.m") } returns false }
        val allowed = testPlayer().also { every { it.hasPermission("gui.m") } returns true }
        assertEquals(GuiOpenResult.NO_PERMISSION, menus.open("m", denied))
        assertEquals(GuiOpenResult.OPENED, menus.open("m", allowed))
        assertEquals(GuiOpenResult.UNKNOWN_MENU, menus.open("nope", allowed))
    }

    @Test
    fun `open_menu opens another menu and unknown references are reported`() {
        write("a.yml", "title: A\nrows: 1\nitems:\n  go: {slot: 0, material: stone, actions: [{open_menu: b}]}\n  bad: {slot: 1, material: stone, actions: [{open_menu: ghost}]}")
        write("b.yml", "title: B\nrows: 1")
        val report = menus.reload()
        assertEquals(listOf("a", "b"), report.loaded)
        assertTrue(report.problems.any { "ghost" in it })
    }

    @Test
    fun `clicking an open_menu item switches to the other menu`() {
        val real = GuiTestHarness(actionsFactory = { m -> nl.klrnbk.minecraft.plugins.gui.common.actions.ProxyActionExecutor(server, m, NOPLogger.NOP_LOGGER) })
        val realMenus = MenuService(dir.resolve("menus"), server, NOPLogger.NOP_LOGGER, api = { real.api })
        write("a.yml", "title: A\nrows: 1\nitems:\n  go: {slot: 0, material: stone, actions: [{open_menu: b}]}")
        write("b.yml", "title: B\nrows: 1")
        realMenus.reload()
        val player = testPlayer()
        realMenus.open("a", player)
        real.protocol.click(player, real.protocol.opsOf<Op.Open>().last().window.containerId, 0)
        assertEquals(realMenus.get("b"), real.api.currentGui(player))
    }

    @Test
    fun `broken files are reported and skipped without hiding the good ones`() {
        write("good.yml", "title: x\nrows: 1")
        write("bad.yml", "title: [")
        write("Bad Name.yml", "title: x\nrows: 1")
        write("notes.txt", "ignored")
        write("dup.yml", "title: x\nrows: 1")
        write("dup.yaml", "title: y\nrows: 1")
        val report = menus.reload()
        assertEquals(listOf("dup", "good"), report.loaded)
        assertTrue(report.problems.any { "bad.yml" in it && "not valid YAML" in it })
        assertTrue(report.problems.any { "Bad Name.yml" in it })
        assertTrue(report.problems.any { "dup" in it && "already defined" in it })
    }

    @Test
    fun `reload swaps the set atomically and drops menus that no longer load`() {
        write("a.yml", "title: A\nrows: 1")
        write("b.yml", "title: B\nrows: 1")
        menus.reload()
        val oldA = menus.get("a")
        write("a.yml", "title: A2\nrows: 2")
        Files.delete(dir.resolve("menus/b.yml"))
        val report = menus.reload()
        assertEquals(listOf("a"), report.loaded)
        assertNull(menus.get("b"))
        assertEquals(18, menus.get("a")!!.size)
        assertEquals(9, oldA!!.size, "viewers of the old instance keep a working gui")
        write("a.yml", "title: [")
        menus.reload()
        assertNull(menus.get("a"), "a menu that now fails is dropped, not silently kept stale")
    }

    @Test
    fun `an item that cannot be built is reported and skipped`() {
        write("m.yml", "title: x\nrows: 1\nitems:\n  a: {slot: 0, material: stone, enchantments: {'Not A Key': 1}}\n  b: {slot: 1, material: stone}")
        val report = menus.reload()
        assertEquals(listOf("m"), report.loaded)
        assertTrue(report.problems.any { "items.a" in it && "could not be built" in it })
        menus.open("m", testPlayer())
        assertNull(window()[0])
        assertNotNull(window()[1])
    }
}
