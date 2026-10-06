package nl.klrnbk.minecraft.plugins.discordId.common

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.toKotlinUuid

class LinkFacadeTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }

    private val alice = identityPlayer("Alice")
    private val bob = identityPlayer("Bob")
    private val missing = identityPlayer("Ghost") // never known to Identity

    private val identityApi: IdentityApi =
        mockk {
            val known = listOf(alice, bob)
            every { getPlayerFromName(any()) } answers { known.firstOrNull { it.name.equals(firstArg<String>(), ignoreCase = true) } }
            every { getPlayerFromUuid(any()) } answers { known.firstOrNull { it.playerId == firstArg() } }
            every { getPlayerFromId(any()) } answers { known.firstOrNull { it.id == firstArg() } }
        }

    init {
        IdentityProvider.register(identityApi)
    }

    private val facade get() = env.linkFacade

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private fun IdentityPlayer.identityId() = id.toKotlinUuid()

    private fun IdentityPlayer.playerUuid() = playerId.toKotlinUuid()

    private fun link(
        player: IdentityPlayer,
        discordId: String,
        name: String = "discord-${player.name}",
        isBooster: Boolean = false,
    ) = env.linkService.linkDiscordWithPlayer(player.identityId(), discordId, name, isBooster)

    // lookupPlayer

    @Test
    fun `lookup of a linked player shows the discord name and id`() {
        link(alice, "111", "alice#1")

        val message = facade.lookupPlayer("alice")

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_SUCCESS, message.messageKey())
        assertEquals(listOf("Alice", "alice#1", "111"), message.messageArguments().map { (it as TextComponent).content() })
        verify { identityApi.getPlayerFromName("alice") }
    }

    @Test
    fun `lookup of a player without a link reports that nothing was found`() {
        val message = facade.lookupPlayer("Alice")

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_FAILED, message.messageKey())
        assertEquals("Alice", (message.messageArguments().single() as TextComponent).content())
    }

    @Test
    fun `lookup of a player who unlinked shows N-A for the discord account`() {
        link(alice, "111")
        env.linkService.unlinkDiscordFromPlayer(alice.identityId())

        val message = facade.lookupPlayer("Alice")

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_SUCCESS, message.messageKey())
        assertEquals(listOf("Alice", "N/A", "N/A"), message.messageArguments().map { (it as TextComponent).content() })
    }

    @Test
    fun `lookup of a player Identity does not know is a failure message`() {
        val message = facade.lookupPlayer("Nobody")

        assertNotEquals(LanguageKeys.LINK_CODE_LOOKUP_SUCCESS, message.messageKey())
    }

    @Test
    fun `lookup of a player Identity does not know uses the lookup failure message`() {
        val message = facade.lookupPlayer("Nobody")

        assertEquals(LanguageKeys.LINK_CODE_LOOKUP_FAILED, message.messageKey())
        assertEquals("Nobody", (message.messageArguments().single() as TextComponent).content())
    }

    // getLinkCodeForPlayer

    @Test
    fun `requesting a code shows it with a click-to-copy event and the expiry`() {
        val message = facade.getLinkCodeForPlayer(alice.playerUuid())
        val code = checkNotNull(env.codeService.getCodeDetailsForPlayerByCode(env.codeRepository.findByEntityId(alice.identityId())!!.code))

        assertEquals(LanguageKeys.LINK_CODE_DETAILS, message.messageKey())
        val (codeComponent, validUntil) = message.messageArguments()
        assertEquals(code.code, (codeComponent as TextComponent).content())
        assertEquals(ClickEvent.copyToClipboard(code.code), codeComponent.clickEvent())
        assertTrue(codeComponent.hoverEvent() is HoverEvent)
        assertTrue(validUntil is Component)
    }

    @Test
    fun `requesting a code twice gives the same code`() {
        val first = facade.getLinkCodeForPlayer(alice.playerUuid()).messageArguments().first() as TextComponent
        val second = facade.getLinkCodeForPlayer(alice.playerUuid()).messageArguments().first() as TextComponent

        assertEquals(first.content(), second.content())
    }

    @Test
    fun `a linked player can not request a code`() {
        link(alice, "111")

        val message = facade.getLinkCodeForPlayer(alice.playerUuid())

        assertEquals(LanguageKeys.LINK_CODE_FAILED, message.messageKey())
        assertNull(env.codeRepository.findByEntityId(alice.identityId()))
    }

    @Test
    fun `a player who just unlinked can not request a code until the cooldown is over`() {
        link(alice, "111")
        env.linkService.unlinkDiscordFromPlayer(alice.identityId())
        assertEquals(LanguageKeys.LINK_CODE_FAILED, facade.getLinkCodeForPlayer(alice.playerUuid()).messageKey())

        env.ageLink(alice.identityId(), 31.days)

        assertEquals(LanguageKeys.LINK_CODE_DETAILS, facade.getLinkCodeForPlayer(alice.playerUuid()).messageKey())
    }

    @Test
    fun `requesting a code for a player Identity does not know is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { facade.getLinkCodeForPlayer(missing.playerUuid()) }
    }

    @Test
    fun `requesting a code while the database is unreachable fails`() {
        env.databaseService.stop()

        assertThrows(Exception::class.java) { facade.getLinkCodeForPlayer(alice.playerUuid()) }
    }

    // linkPlayer

    private fun codeOf(player: IdentityPlayer): String = (facade.getLinkCodeForPlayer(player.playerUuid()).messageArguments().first() as TextComponent).content()

    @Test
    fun `a valid code links the discord account and is used up`() {
        val code = codeOf(alice)

        val result = facade.linkPlayer(code, "111", "alice#1", isBooster = true)

        assertEquals(LanguageKeys.LINK_CODE_SUCCESS, result)
        val details = checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId()))
        assertEquals("111", details.discordId)
        assertEquals("alice#1", details.discordName)
        assertTrue(details.isBooster)
        assertNull(env.codeRepository.findByEntityId(alice.identityId()))
        // The used code can't be used again.
        assertEquals(LanguageKeys.LINK_CODE_INVALID, facade.linkPlayer(code, "222", "bob#1", false))
    }

    @Test
    fun `an expired code that is not cleaned up yet is invalid`() {
        val code = codeOf(alice)
        env.expireCode(alice.identityId())

        assertEquals(LanguageKeys.LINK_CODE_INVALID, facade.linkPlayer(code, "111", "alice#1", false))
    }

    @Test
    fun `an unknown code is invalid`() {
        assertEquals(LanguageKeys.LINK_CODE_INVALID, facade.linkPlayer("nope", "111", "alice#1", false))
        assertNull(env.linkService.getLinkDetailsByDiscordId("111"))
    }

    @Test
    fun `an expired code that was cleaned up is invalid`() {
        val code = codeOf(alice)
        env.expireCode(alice.identityId())
        env.databaseService.performCleanup()

        assertEquals(LanguageKeys.LINK_CODE_INVALID, facade.linkPlayer(code, "111", "alice#1", false))
        assertNull(env.linkService.getLinkDetailsByDiscordId("111"))
    }

    @Test
    fun `a discord account that is already linked can not be linked to another player`() {
        link(bob, "111")
        val code = codeOf(alice)

        assertEquals(LanguageKeys.LINK_CODE_ALREADY_LINKED, facade.linkPlayer(code, "111", "alice#1", false))

        assertNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId()))
        assertNotNull(env.codeRepository.findByEntityId(alice.identityId()), "the code is kept so another account can still use it")
    }

    @Test
    fun `a code of a player Identity no longer knows is rejected`() {
        val code = env.codeService.getOrCreateCodeDetailsForPlayer(missing.identityId()).code

        assertThrows(IllegalArgumentException::class.java) { facade.linkPlayer(code, "111", "ghost#1", false) }
    }

    @Test
    fun `linking while the database is unreachable fails`() {
        val code = codeOf(alice)
        env.databaseService.stop()

        assertThrows(Exception::class.java) { facade.linkPlayer(code, "111", "alice#1", false) }
    }

    // unlinkPlayer / forceUnlinkPlayer

    @Test
    fun `a player can not unlink during the cooldown`() {
        link(alice, "111")

        val message = facade.unlinkPlayer(alice.playerUuid(), isForced = false, isBypassed = false)

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, message.messageKey())
        assertTrue(checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId())).isLinked)
    }

    @Test
    fun `a player can unlink after the cooldown`() {
        link(alice, "111")
        env.ageLink(alice.identityId(), 31.days)

        val message = facade.unlinkPlayer(alice.playerUuid(), isForced = false, isBypassed = false)

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, message.messageKey())
        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId())).isLinked)
    }

    @Test
    fun `the bypass permission skips the cooldown`() {
        link(alice, "111")

        val message = facade.unlinkPlayer(alice.playerUuid(), isForced = false, isBypassed = true)

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, message.messageKey())
    }

    @Test
    fun `unlinking a player Identity does not know is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { facade.unlinkPlayer(missing.playerUuid(), false, false) }
    }

    @Test
    fun `unlinking a player who never linked is a failure message`() {
        val message = facade.unlinkPlayer(alice.playerUuid(), isForced = false, isBypassed = false)

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, message.messageKey())
    }

    @Test
    fun `an admin can force unlink during the cooldown`() {
        link(alice, "111")

        val message = facade.forceUnlinkPlayer("alice")

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_SUCCESS, message.messageKey())
        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId())).isLinked)
    }

    @Test
    fun `force unlinking a player who never linked is a failure message`() {
        val message = facade.forceUnlinkPlayer("alice")

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, message.messageKey())
    }

    @Test
    fun `unlinking a player who already unlinked changes nothing, not even when forced`() {
        link(alice, "111")
        env.linkService.unlinkDiscordFromPlayer(alice.identityId())
        val before = checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId())).lastUpdatedAt

        val message = facade.forceUnlinkPlayer("alice")

        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, message.messageKey())
        assertEquals(before, checkNotNull(env.linkService.getLinkDetailsByIdentityId(alice.identityId())).lastUpdatedAt)
    }

    @Test
    fun `force unlinking an unknown player is a failure message`() {
        assertEquals(LanguageKeys.LINK_CODE_UNLINK_FAILED, facade.forceUnlinkPlayer("Nobody").messageKey())
    }

    // Discord -> Minecraft and updates

    @Test
    fun `a discord user is resolved to their minecraft name`() {
        link(alice, "111")

        assertEquals("Alice", facade.getMinecraftUsernameOfDiscordUser("111"))
        assertNull(facade.getMinecraftUsernameOfDiscordUser("999"))
    }

    @Test
    fun `a link whose Identity player is gone resolves to nothing`() {
        env.linkService.linkDiscordWithPlayer(missing.identityId(), "111", "ghost#1", false)

        assertNull(facade.getMinecraftUsernameOfDiscordUser("111"))
    }

    @Test
    fun `discord name and booster updates are applied to linked players only`() {
        link(alice, "111", "old")

        facade.updateDiscordNameForLinkedPlayer("111", "new")
        facade.updateDiscordBoosterStatusForLinkedPlayer("111", true)
        facade.updateDiscordNameForLinkedPlayer("unlinked", "x")
        facade.updateDiscordBoosterStatusForLinkedPlayer("unlinked", true)

        val details = checkNotNull(env.linkService.getLinkDetailsByDiscordId("111"))
        assertEquals("new", details.discordName)
        assertTrue(details.isBooster)
        assertNull(env.linkService.getLinkDetailsByDiscordId("unlinked"))
    }
}
