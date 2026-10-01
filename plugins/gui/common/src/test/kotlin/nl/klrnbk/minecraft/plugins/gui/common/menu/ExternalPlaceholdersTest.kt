package nl.klrnbk.minecraft.plugins.gui.common.menu

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.common.GuiTestHarness
import nl.klrnbk.minecraft.plugins.gui.common.FakeGuiProtocol.Op
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture

class ExternalPlaceholdersTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val player = testPlayer("Alex")

    // A class loader that sees only the JDK stands in for "the plugin is not installed".
    private val noPlugins = object : ClassLoader(null) {}

    // ---- legacy colours ------------------------------------------------------------------------

    @Test
    fun `legacy section codes become MiniMessage tags`() {
        assertEquals("<green>Alex<reset> ok", LegacyColors.toMiniMessage("§aAlex§r ok"))
        assertEquals("<bold><red>x", LegacyColors.toMiniMessage("§l§cx"))
        assertEquals("<#ff8800>hex", LegacyColors.toMiniMessage("§x§f§f§8§8§0§0hex"))
        assertEquals("plain 100%", LegacyColors.toMiniMessage("plain 100%"))
        assertEquals("§z stays", LegacyColors.toMiniMessage("§z stays"))
    }

    // ---- PAPIProxyBridge -----------------------------------------------------------------------

    private class Fake {
        val calls = mutableListOf<Pair<UUID, String>>()
        val pending = mutableListOf<CompletableFuture<String>>()
        val formatter = PapiFormatter { uuid, text -> CompletableFuture<String>().also { calls += uuid to text; pending += it } }
    }

    @Test
    fun `first format blanks the placeholders, starts one fetch, and re-renders when the answer differs`() {
        val fake = Fake()
        val changed = mutableListOf<String>()
        val papi = PapiPlaceholders(fake.formatter, NOPLogger.NOP_LOGGER, onChanged = { changed += it.username })

        assertEquals("Balance: ", papi.format(player, "Balance: %balance%"))
        assertEquals("Balance: ", papi.format(player, "Balance: %balance%"), "still pending")
        assertEquals(1, fake.calls.size, "no duplicate in-flight request")

        fake.pending[0].complete("Balance: 100")
        assertEquals(listOf("Alex"), changed)
        assertEquals("Balance: 100", papi.format(player, "Balance: %balance%"))
    }

    @Test
    fun `an unchanged answer does not trigger another re-render`() {
        val fake = Fake()
        var now = 0L
        val changed = mutableListOf<String>()
        val papi = PapiPlaceholders(fake.formatter, NOPLogger.NOP_LOGGER, onChanged = { changed += "x" }, ttlMillis = 1000, clock = { now })
        papi.format(player, "%balance%")
        fake.pending[0].complete("100")
        assertEquals(1, changed.size)

        now = 5000 // stale: a refetch starts, the old value is still returned meanwhile
        assertEquals("100", papi.format(player, "%balance%"))
        assertEquals(2, fake.calls.size)
        fake.pending[1].complete("100")
        assertEquals(1, changed.size, "same value: no loop")

        now = 10_000
        papi.format(player, "%balance%")
        fake.pending[2].complete("200")
        assertEquals(2, changed.size)
    }

    @Test
    fun `values are cached per player and text, and legacy colours are translated`() {
        val fake = Fake()
        val papi = PapiPlaceholders(fake.formatter, NOPLogger.NOP_LOGGER, onChanged = {})
        val other = testPlayer("Bea")
        papi.format(player, "%a%")
        papi.format(other, "%a%")
        papi.format(player, "%b%")
        assertEquals(3, fake.calls.size)
        fake.pending[0].complete("§aGreen")
        assertEquals("<green>Green", papi.format(player, "%a%"))
        assertEquals("", papi.format(other, "%a%"))
    }

    @Test
    fun `errors are logged once, leave blanks, and are retried later`() {
        val fake = Fake()
        val warnings = mutableListOf<String>()
        val logger = object : org.slf4j.Logger by NOPLogger.NOP_LOGGER {
            override fun warn(format: String, arg1: Any?, arg2: Any?) {
                warnings += format
            }
        }
        val papi = PapiPlaceholders(fake.formatter, logger, onChanged = {})
        papi.format(player, "%a%")
        fake.pending[0].completeExceptionally(RuntimeException("timeout"))
        assertEquals("", papi.format(player, "%a%"))
        fake.pending[1].completeExceptionally(RuntimeException("timeout"))
        assertEquals(1, warnings.size)
        assertEquals(2, fake.calls.size, "a failed fetch is retried on the next render")
    }

    @Test
    fun `a formatter that throws does not break rendering`() {
        val papi = PapiPlaceholders({ _, _ -> error("bridge gone") }, NOPLogger.NOP_LOGGER, onChanged = {})
        assertEquals("x ", papi.format(player, "x %a%"))
    }

    @Test
    fun `forget drops the player's cache`() {
        val fake = Fake()
        val papi = PapiPlaceholders(fake.formatter, NOPLogger.NOP_LOGGER, onChanged = {})
        papi.format(player, "%a%")
        fake.pending[0].complete("1")
        assertEquals(1, papi.cachedEntries)
        papi.forget(player.uniqueId)
        assertEquals(0, papi.cachedEntries)
    }

    @Test
    fun `the placeholder pattern needs both percent signs`() {
        assertTrue(PapiPlaceholders.PATTERN.containsMatchIn("hi %player_name%"))
        assertTrue(PapiPlaceholders.PATTERN.containsMatchIn("%luckperms_prefix%"))
        assertFalse(PapiPlaceholders.PATTERN.containsMatchIn("100% sure"))
        assertFalse(PapiPlaceholders.PATTERN.containsMatchIn("50% and 60%"))
    }

    @Test
    fun `the reflective bridge finds PAPIProxyBridge's api or reports absence`() {
        val real = PapiProxyBridgeFormatter()
        assertTrue(real.available)
        assertEquals("100 and §aAlex", real.format(player.uniqueId, "%balance% and %player_name%").get())
        assertFalse(PapiProxyBridgeFormatter(noPlugins).available)
    }

    // ---- MiniPlaceholders ----------------------------------------------------------------------

    @Test
    fun `MiniPlaceholders tags resolve for a viewer when installed and stay literal when not`() {
        val with = ExternalPlaceholders(MiniPlaceholdersBridge(), null)
        assertEquals("[VIP] Alex", plain.serialize(with.render("<vip_prefix> Alex", player)))
        val without = ExternalPlaceholders(null, null)
        assertEquals("<vip_prefix> Alex", plain.serialize(without.render("<vip_prefix> Alex", player)))
        assertFalse(MiniPlaceholdersBridge(noPlugins).available)
        assertEquals(null, MiniPlaceholdersBridge(noPlugins).resolver())
    }

    @Test
    fun `plain MiniMessage still works with no player`() {
        val text = ExternalPlaceholders(MiniPlaceholdersBridge(), null).render("<red>x", null)
        assertEquals(Component.text("x", net.kyori.adventure.text.format.NamedTextColor.RED), text)
    }

    @Test
    fun `items are per-viewer only when an integration could change them`() {
        val none = ExternalPlaceholders.NONE
        assertFalse(none.needsPerViewer("<red>x %a%"))
        val papi = PapiPlaceholders({ _, _ -> CompletableFuture() }, NOPLogger.NOP_LOGGER, {})
        assertTrue(ExternalPlaceholders(null, papi).needsPerViewer("%a%"))
        assertFalse(ExternalPlaceholders(null, papi).needsPerViewer("plain 100%"))
        assertTrue(ExternalPlaceholders(MiniPlaceholdersBridge(), null).needsPerViewer("<vip_prefix>"))
        assertFalse(ExternalPlaceholders(MiniPlaceholdersBridge(), null).needsPerViewer("no tags"))
    }

    // ---- end to end through a YAML menu --------------------------------------------------------

    @TempDir
    lateinit var dir: Path

    @Test
    fun `a yaml menu shows papi and miniplaceholders values and refreshes when the async answer arrives`() {
        val harness = GuiTestHarness(platforms = { GuiPlatform.JAVA })
        val server = mockk<com.velocitypowered.api.proxy.ProxyServer>(relaxed = true) { every { playerCount } returns 1 }
        val pending = mutableListOf<CompletableFuture<String>>()
        lateinit var api: nl.klrnbk.minecraft.plugins.gui.api.GuiApi
        val papi = PapiPlaceholders({ _, _ -> CompletableFuture<String>().also { pending += it } }, NOPLogger.NOP_LOGGER, onChanged = { p -> api.currentGui(p)?.refresh(p) })
        val menus = MenuService(dir.resolve("menus"), server, NOPLogger.NOP_LOGGER, api = { harness.api }, external = ExternalPlaceholders(MiniPlaceholdersBridge(), papi))
        api = harness.api
        Files.createDirectories(dir.resolve("menus"))
        Files.writeString(dir.resolve("menus/m.yml"), "title: x\nrows: 1\nitems:\n  a:\n    slot: 0\n    material: paper\n    name: '<vip_prefix> {player}'\n    lore: ['Coins: %balance%']\n")
        menus.reload()

        menus.open("m", player)
        val first = harness.protocol.opsOf<Op.Open>().last().window.items[0]!!
        assertEquals("[VIP] Alex", plain.serialize(first.name!!))
        assertEquals("Coins: ", plain.serialize(first.lore.single()))

        pending.single().complete("Coins: 250")
        val update = harness.protocol.opsOf<Op.Update>().last().changes[0]!!
        assertEquals("Coins: 250", plain.serialize(update.lore.single()))
    }
}
