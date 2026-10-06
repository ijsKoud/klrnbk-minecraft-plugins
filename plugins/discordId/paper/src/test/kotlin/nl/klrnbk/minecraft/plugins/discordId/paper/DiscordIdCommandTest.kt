package nl.klrnbk.minecraft.plugins.discordId.paper

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.Permissions
import nl.klrnbk.minecraft.plugins.discordId.common.identityPlayer
import nl.klrnbk.minecraft.plugins.discordId.common.registerIdentity
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.toKotlinUuid

private fun TextComponent.translatable(): TranslatableComponent = children().filterIsInstance<TranslatableComponent>().single()

private fun TextComponent.messageKey(): String = translatable().key()

private fun TextComponent.messageArguments(): List<Component> = translatable().arguments().map { it.asComponent() }

class DiscordIdCommandTest {
    private val harness = PaperTestHarness()
    private val server get() = harness.server

    private lateinit var aliceIdentity: IdentityPlayer
    private lateinit var alice: PlayerMock
    private lateinit var bobIdentity: IdentityPlayer

    private val linkService get() = harness.linkService

    @AfterEach
    fun tearDown() = harness.close()

    private fun setUp(): Unit {
        alice = server.addPlayer("Alice")
        aliceIdentity = identityPlayer("Alice").copy(playerId = alice.uniqueId)
        bobIdentity = identityPlayer("Bob")
        registerIdentity(aliceIdentity, bobIdentity)
        harness.load(useProxy = true)
        harness.startFully()
    }

    private fun grant(
        player: PlayerMock,
        vararg permissions: String,
    ) = permissions.forEach { player.addAttachment(harness.plugin, it, true) }

    private fun PlayerMock.nextText(): TextComponent = nextComponentMessage() as TextComponent

    private fun key(component: Component): String? = ((component as? TextComponent)?.children()?.firstOrNull() as? TranslatableComponent)?.key()

    @Test
    fun `link sends the player their code, click to copy`() {
        setUp()
        grant(alice, Permissions.LINK)

        server.dispatchCommand(alice, "discordid link")

        val message = alice.nextText()
        assertEquals(LanguageKeys.LINK_CODE_DETAILS, message.messageKey())
        val code = message.messageArguments().first() as TextComponent
        assertEquals(ClickEvent.copyToClipboard(code.content()), code.clickEvent())
        assertEquals(code.content(), harness.codeRepository.findByEntityId(aliceIdentity.id.toKotlinUuid())?.code)
    }

    @Test
    fun `without permission the player gets vanillas unknown command message`() {
        setUp()

        server.dispatchCommand(alice, "discordid link")

        val message = alice.nextComponentMessage()
        assertEquals("commands.help.failed", (message as TranslatableComponent).key())
    }

    @Test
    fun `link is for players only`() {
        setUp()

        server.dispatchCommand(server.consoleSender, "discordid link")

        // The console is allowed everything, but there is no player to link.
        assertNull(harness.codeRepository.findByEntityId(aliceIdentity.id.toKotlinUuid()))
    }

    @Test
    fun `link for a linked player says they can not link`() {
        setUp()
        grant(alice, Permissions.LINK)
        linkService.linkDiscordWithPlayer(aliceIdentity.id.toKotlinUuid(), "111", "alice#1", false)

        server.dispatchCommand(alice, "discordid link")

        assertEquals(LanguageKeys.LINK_CODE_FAILED, alice.nextText().messageKey())
    }

    @Test
    fun `unlink during the cooldown is refused and after the cooldown works`() {
        setUp()
        grant(alice, Permissions.UNLINK)
        val id = aliceIdentity.id.toKotlinUuid()
        linkService.linkDiscordWithPlayer(id, "111", "alice#1", false)

        server.dispatchCommand(alice, "discordid unlink")
        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, alice.nextText().messageKey())

        harness.ageLink(id, 31.days)
        server.dispatchCommand(alice, "discordid unlink")
        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, alice.nextText().messageKey())
        assertFalse(checkNotNull(linkService.getLinkDetailsByIdentityId(id)).isLinked)
    }

    @Test
    fun `the bypass permission unlinks during the cooldown`() {
        setUp()
        grant(alice, Permissions.UNLINK, Permissions.UNLINK_BYPASS)
        linkService.linkDiscordWithPlayer(aliceIdentity.id.toKotlinUuid(), "111", "alice#1", false)

        server.dispatchCommand(alice, "discordid unlink")

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, alice.nextText().messageKey())
    }

    @Test
    fun `lookup shows the linked discord account`() {
        setUp()
        grant(alice, Permissions.LOOKUP)
        linkService.linkDiscordWithPlayer(bobIdentity.id.toKotlinUuid(), "222", "bob#1", false)

        server.dispatchCommand(alice, "discordid lookup Bob")

        val message = alice.nextText()
        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_SUCCESS, message.messageKey())
        assertEquals(listOf("Bob", "bob#1", "222"), message.messageArguments().map { (it as TextComponent).content() })
    }

    @Test
    fun `lookup without a player name shows the usage`() {
        setUp()
        grant(alice, Permissions.LOOKUP)

        assertFalse(server.dispatchCommand(alice, "discordid lookup"))
        assertNull(alice.nextComponentMessage()?.takeIf { key(it) == LanguageKeys.LINK_CODE_LOOKUP_SUCCESS })
    }

    @Test
    fun `adminunlink force unlinks during the cooldown`() {
        setUp()
        grant(alice, Permissions.UNLINK_FORCED)
        val bobId = bobIdentity.id.toKotlinUuid()
        linkService.linkDiscordWithPlayer(bobId, "222", "bob#1", false)

        server.dispatchCommand(alice, "discordid adminunlink Bob")

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, alice.nextText().messageKey())
        assertFalse(checkNotNull(linkService.getLinkDetailsByIdentityId(bobId)).isLinked)
    }

    @Test
    fun `adminunlink is logged with the admin as the actor`() {
        setUp()
        grant(alice, Permissions.UNLINK_FORCED)
        val bobId = bobIdentity.id.toKotlinUuid()
        linkService.linkDiscordWithPlayer(bobId, "222", "bob#1", false)

        server.dispatchCommand(alice, "discordid adminunlink Bob")

        val entry =
            harness.injector
                .getInstance(nl.klrnbk.minecraft.plugins.discordId.common.services.audit.AuditLogService::class.java)
                .getLogs(nl.klrnbk.minecraft.packages.database.QueryPagination())
                .single()
        assertEquals(aliceIdentity.id.toKotlinUuid(), entry.actorIdentityId)
        assertEquals(bobId, entry.targetIdentityId)
    }

    @Test
    fun `export writes a file off the main thread and reports it`() {
        setUp()
        grant(alice, Permissions.ADMIN_EXPORT)

        server.dispatchCommand(alice, "discordid export")
        server.scheduler.waitAsyncTasksFinished()

        assertEquals(LanguageKeys.LINK_CODE_EXPORT_SUCCESS, alice.nextText().messageKey())
        assertTrue(harness.dataFolder.resolve("exports").listFiles().orEmpty().any { it.name.endsWith(".zip") })
    }

    @Test
    fun `import of a missing file reports the failure`() {
        setUp()
        grant(alice, Permissions.ADMIN_IMPORT)

        server.dispatchCommand(alice, "discordid import missing.zip")
        server.scheduler.waitAsyncTasksFinished()

        assertEquals(LanguageKeys.LINK_CODE_TRANSFER_FAILED, alice.nextText().messageKey())
    }

    @Test
    fun `reload restarts the bot and the database`() {
        setUp()
        grant(alice, Permissions.ADMIN_RELOAD)

        server.dispatchCommand(alice, "discordid reload")
        server.scheduler.waitAsyncTasksFinished()

        assertEquals(LanguageKeys.LINK_CODE_RELOAD_SUCCESS, alice.nextText().messageKey())
        assertEquals(1, harness.botMain.stops)
        assertEquals(2, harness.botMain.starts)
    }

    @Test
    fun `tab completion only offers the subcommands the sender may use`() {
        setUp()
        grant(alice, Permissions.LINK, Permissions.LOOKUP)
        val command = checkNotNull(server.commandMap.getCommand("discordid"))

        assertEquals(listOf("link", "lookup"), command.tabComplete(alice, "discordid", arrayOf("")))
        assertEquals(listOf("lookup"), command.tabComplete(alice, "discordid", arrayOf("lo")))
    }

    @Test
    fun `tab completion suggests player names from Identity`() {
        setUp()
        grant(alice, Permissions.LOOKUP)
        val command = checkNotNull(server.commandMap.getCommand("discordid"))

        assertEquals(listOf("Bob"), command.tabComplete(alice, "discordid", arrayOf("lookup", "b")))
    }

    @Test
    fun `link for a player Identity does not know fails loudly`() {
        setUp()
        val stranger = server.addPlayer("Stranger")
        grant(stranger, Permissions.LINK)

        assertThrows(Exception::class.java) { server.dispatchCommand(stranger, "discordid link") }
    }
}
