package nl.klrnbk.minecraft.plugins.discordId.velocity

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import nl.klrnbk.minecraft.plugins.discordId.common.DiscordIdTestEnvironment
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.Permissions
import nl.klrnbk.minecraft.plugins.discordId.common.identityPlayer
import nl.klrnbk.minecraft.plugins.discordId.common.messageArguments
import nl.klrnbk.minecraft.plugins.discordId.common.messageKey
import nl.klrnbk.minecraft.plugins.discordId.common.registerIdentity
import nl.klrnbk.minecraft.plugins.discordId.velocity.commands.DiscordIdCommand
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.toKotlinUuid

class DiscordIdCommandTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }
    private val alice = identityPlayer("Alice")
    private val bob = identityPlayer("Bob")
    private val dispatcher = CommandDispatcher<CommandSource>()
    private val sent = mutableListOf<Component>()

    private val command: DiscordIdCommand

    init {
        registerIdentity(alice, bob)
        command = DiscordIdCommand(env.adminCommandsFacade, env.linkFacade, env.dataDirectory)
        dispatcher.root.addChild(command.configure().node)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private fun player(
        who: nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer,
        vararg permissions: String,
    ): CommandSource =
        mockk<Player> {
            every { uniqueId } returns who.playerId
            every { hasPermission(any()) } answers { firstArg<String>() in permissions }
            every { sendMessage(any<Component>()) } answers { sent.add(firstArg()) }
        }

    private fun console(vararg permissions: String): CommandSource =
        mockk {
            every { hasPermission(any()) } answers { firstArg<String>() in permissions }
            every { sendMessage(any<Component>()) } answers { sent.add(firstArg()) }
        }

    private fun run(
        input: String,
        source: CommandSource,
    ) = dispatcher.execute(input, source)

    private fun lastMessage() = sent.last() as TextComponent

    private fun identityId(who: nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer) = who.id.toKotlinUuid()

    // link

    @Test
    fun `link sends the player their code, click to copy`() {
        run("discordId link", player(alice, Permissions.LINK))

        assertEquals(LanguageKeys.LINK_CODE_DETAILS, lastMessage().messageKey())
        val code = lastMessage().messageArguments().first() as TextComponent
        assertEquals(env.codeRepository.findByEntityId(identityId(alice))?.code, code.content())
        assertEquals(ClickEvent.copyToClipboard(code.content()), code.clickEvent())
    }

    @Test
    fun `link without permission is not available`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId link", player(alice)) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `link is not available to the console`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId link", console(Permissions.LINK)) }
    }

    @Test
    fun `link for a linked player says they can not link`() {
        env.linkService.linkDiscordWithPlayer(identityId(alice), "111", "alice#1", false)

        run("discordId link", player(alice, Permissions.LINK))

        assertEquals(LanguageKeys.LINK_CODE_FAILED, lastMessage().messageKey())
    }

    @Test
    fun `link for a player Identity does not know fails loudly`() {
        val stranger = identityPlayer("Stranger")

        assertThrows(IllegalArgumentException::class.java) { run("discordId link", player(stranger, Permissions.LINK)) }
    }

    @Test
    fun `link while the database is unreachable fails`() {
        env.databaseService.stop()

        assertThrows(Exception::class.java) { run("discordId link", player(alice, Permissions.LINK)) }
    }

    // unlink

    @Test
    fun `unlink during the cooldown is refused`() {
        env.linkService.linkDiscordWithPlayer(identityId(alice), "111", "alice#1", false)

        run("discordId unlink", player(alice, Permissions.UNLINK))

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, lastMessage().messageKey())
        assertTrue(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId(alice))).isLinked)
    }

    @Test
    fun `unlink after the cooldown removes the link`() {
        env.linkService.linkDiscordWithPlayer(identityId(alice), "111", "alice#1", false)
        env.ageLink(identityId(alice), 31.days)

        run("discordId unlink", player(alice, Permissions.UNLINK))

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, lastMessage().messageKey())
        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId(alice))).isLinked)
    }

    @Test
    fun `the bypass permission unlinks during the cooldown`() {
        env.linkService.linkDiscordWithPlayer(identityId(alice), "111", "alice#1", false)

        run("discordId unlink", player(alice, Permissions.UNLINK, Permissions.UNLINK_BYPASS))

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, lastMessage().messageKey())
    }

    @Test
    fun `unlink without permission or from the console is not available`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId unlink", player(alice)) }
        assertThrows(CommandSyntaxException::class.java) { run("discordId unlink", console(Permissions.UNLINK)) }
    }

    // lookup

    @Test
    fun `lookup shows the linked discord account`() {
        env.linkService.linkDiscordWithPlayer(identityId(bob), "222", "bob#1", false)

        run("discordId lookup Bob", console(Permissions.LOOKUP))

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_SUCCESS, lastMessage().messageKey())
        assertEquals(listOf("Bob", "bob#1", "222"), lastMessage().messageArguments().map { (it as TextComponent).content() })
    }

    @Test
    fun `lookup of a player without a link says so`() {
        run("discordId lookup Alice", console(Permissions.LOOKUP))

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_FAILED, lastMessage().messageKey())
    }

    @Test
    fun `lookup needs the permission and a player name`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId lookup Bob", console()) }
        assertThrows(CommandSyntaxException::class.java) { run("discordId lookup", console(Permissions.LOOKUP)) }
    }

    @Test
    fun `lookup suggests player names from Identity`() {
        val parse = dispatcher.parse("discordId lookup a", console(Permissions.LOOKUP))

        val suggestions = dispatcher.getCompletionSuggestions(parse).get().list.map { it.text }

        assertEquals(listOf("Alice"), suggestions)
    }

    // adminunlink / import

    @Test
    fun `adminunlink force unlinks a player during the cooldown`() {
        env.linkService.linkDiscordWithPlayer(identityId(bob), "222", "bob#1", false)

        run("discordId adminunlink Bob", console(Permissions.UNLINK_FORCED))

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, lastMessage().messageKey())
        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId(bob))).isLinked)
    }

    @Test
    fun `adminunlink needs the permission and a player name`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId adminunlink Bob", console()) }
        assertThrows(CommandSyntaxException::class.java) { run("discordId adminunlink", console(Permissions.UNLINK_FORCED)) }
    }

    @Test
    fun `import of a missing file reports the failure`() {
        run("discordId import missing.zip", console(Permissions.ADMIN_IMPORT))

        assertEquals(LanguageKeys.LINK_CODE_TRANSFER_FAILED, lastMessage().messageKey())
    }

    @Test
    fun `import needs the permission and a file name`() {
        assertThrows(CommandSyntaxException::class.java) { run("discordId import x.zip", console()) }
        assertThrows(CommandSyntaxException::class.java) { run("discordId import", console(Permissions.ADMIN_IMPORT)) }
    }

    // Registration

    @Test
    fun `the command is registered with its aliases`() {
        val builder = mockk<CommandMeta.Builder>()
        val meta = mockk<CommandMeta>()
        val manager = mockk<CommandManager> { every { metaBuilder(any<com.velocitypowered.api.command.BrigadierCommand>()) } returns builder }
        every { builder.aliases(*anyVararg()) } returns builder
        every { builder.plugin(any()) } returns builder
        every { builder.build() } returns meta
        val plugin = Any()

        assertEquals(meta, command.meta(manager, plugin))

        verify { builder.aliases("klrnbk-discordId", "discordid") }
        verify { builder.plugin(plugin) }
    }

    @Test
    fun `building the command without the Identity plugin fails with a clear message`() {
        IdentityProvider.unregister()

        val failure = assertThrows(IllegalStateException::class.java) { command.configure() }

        assertNotNull(failure.message)
        assertTrue(failure.message!!.contains("Identity"))
        assertNull(sent.lastOrNull())
    }
}
