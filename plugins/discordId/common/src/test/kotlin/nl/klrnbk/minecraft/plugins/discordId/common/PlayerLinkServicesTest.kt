package nl.klrnbk.minecraft.plugins.discordId.common

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class PlayerLinkServicesTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }
    private val player = Uuid.random()

    @AfterEach
    fun cleanup() = env.close()

    // PlayerLinkService

    @Test
    fun `linking a player stores the discord account`() {
        val details = env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", true)

        assertTrue(details.isLinked)
        assertEquals("discord-1", details.discordId)
        assertEquals("alice", details.discordName)
        assertTrue(details.isBooster)
        assertEquals(player, env.linkService.getLinkDetailsByDiscordId("discord-1")?.identityId)
        assertEquals("alice", env.linkService.getLinkDetailsByIdentityId(player)?.discordName)
    }

    @Test
    fun `unknown players have no link details`() {
        assertNull(env.linkService.getLinkDetailsByIdentityId(player))
        assertNull(env.linkService.getLinkDetailsByDiscordId("nobody"))
    }

    @Test
    fun `an already linked player can not be linked again`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)

        assertThrows(IllegalArgumentException::class.java) { env.linkService.linkDiscordWithPlayer(player, "discord-2", "bob", false) }
    }

    @Test
    fun `a player who unlinked can link a new account`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", true)
        env.linkService.unlinkDiscordFromPlayer(player)

        val details = env.linkService.linkDiscordWithPlayer(player, "discord-2", "bob", false)

        assertEquals("discord-2", details.discordId)
        assertFalse(details.isBooster)
    }

    @Test
    fun `unlinking clears the discord account and the booster status`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", true)

        val details = env.linkService.unlinkDiscordFromPlayer(player)

        assertFalse(details.isLinked)
        assertNull(details.discordId)
        assertNull(details.discordName)
        assertFalse(details.isBooster)
        assertNull(env.linkService.getLinkDetailsByDiscordId("discord-1"))
    }

    @Test
    fun `unlinking a player who never linked is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { env.linkService.unlinkDiscordFromPlayer(player) }
    }

    @Test
    fun `username and booster updates keep the rest of the link`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)

        env.linkService.updateDiscordUsernameForLinkedPlayer(player, "alice2")
        val details = env.linkService.updateBoosterStatusForLinkedPlayer(player, true)

        assertEquals("alice2", details.discordName)
        assertEquals("discord-1", details.discordId)
        assertTrue(details.isBooster)
    }

    @Test
    fun `updating an unknown player is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { env.linkService.updateDiscordUsernameForLinkedPlayer(player, "x") }
        assertThrows(IllegalArgumentException::class.java) { env.linkService.updateBoosterStatusForLinkedPlayer(player, true) }
    }

    @Test
    fun `getAllLinkedPlayers lists every link`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)
        env.linkService.linkDiscordWithPlayer(Uuid.random(), "discord-2", "bob", false)

        assertEquals(2, env.linkService.getAllLinkedPlayers().size)
    }

    @Test
    fun `a player and a discord account can be linked when neither is taken`() {
        assertTrue(env.linkService.canLinkDiscordToPlayer(player, "discord-1"))
    }

    @Test
    fun `a taken discord account or an already linked player can not be linked`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)

        assertFalse(env.linkService.canLinkDiscordToPlayer(Uuid.random(), "discord-1"))
        assertFalse(env.linkService.canLinkDiscordToPlayer(player, "discord-2"))
    }

    @Test
    fun `unlinking is only possible after the cooldown`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)
        assertFalse(env.linkService.canUnlinkDiscordFromPlayer(player))

        env.ageLink(player, 31.days)

        assertTrue(env.linkService.canUnlinkDiscordFromPlayer(player))
    }

    @Test
    fun `checking the unlink cooldown of a player who never linked is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { env.linkService.canUnlinkDiscordFromPlayer(player) }
    }

    @Test
    fun `players who never linked can request a link code`() {
        assertTrue(env.linkService.canRequestLinkCode(player))
    }

    @Test
    fun `linked players can not request a link code`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)
        env.ageLink(player, 31.days)

        assertFalse(env.linkService.canRequestLinkCode(player))
    }

    @Test
    fun `players who just unlinked have to wait out the cooldown before requesting a code`() {
        env.linkService.linkDiscordWithPlayer(player, "discord-1", "alice", false)
        env.linkService.unlinkDiscordFromPlayer(player)
        assertFalse(env.linkService.canRequestLinkCode(player))

        env.ageLink(player, 31.days)

        assertTrue(env.linkService.canRequestLinkCode(player))
    }

    // PlayerLinkCodeService

    @Test
    fun `a player gets the same code until it is used`() {
        val first = env.codeService.getOrCreateCodeDetailsForPlayer(player)
        val second = env.codeService.getOrCreateCodeDetailsForPlayer(player)

        assertEquals(first.code, second.code)
        assertEquals(player, first.playerEntityId)
    }

    @Test
    fun `a code can be looked up by its value`() {
        val code = env.codeService.getOrCreateCodeDetailsForPlayer(player)

        assertEquals(player, env.codeService.getCodeDetailsForPlayerByCode(code.code)?.playerEntityId)
        assertNull(env.codeService.getCodeDetailsForPlayerByCode("wrong"))
    }

    @Test
    fun `a deleted code is gone and a new one is generated`() {
        val first = env.codeService.getOrCreateCodeDetailsForPlayer(player)

        assertTrue(env.codeService.deleteCodeDetailsForPlayer(player))
        assertFalse(env.codeService.deleteCodeDetailsForPlayer(player))
        assertNull(env.codeService.getCodeDetailsForPlayerByCode(first.code))
        assertNotNull(env.codeService.getOrCreateCodeDetailsForPlayer(player))
    }

    @Test
    fun `codes disappear once expired codes are cleaned up`() {
        val code = env.codeService.getOrCreateCodeDetailsForPlayer(player)
        env.expireCode(player)

        env.databaseService.performCleanup()

        assertNull(env.codeService.getCodeDetailsForPlayerByCode(code.code))
    }
}
