package nl.klrnbk.minecraft.plugins.discordId.common

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class RepositoriesTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }

    @AfterEach
    fun cleanup() = env.close()

    // Link repository

    @Test
    fun `a created link can be found by identity id and by discord id`() {
        val id = Uuid.random()
        env.linkRepository.create(id, "discord-1", "alice", true)

        val byIdentity = checkNotNull(env.linkRepository.findByIdentityId(id))
        val byDiscord = checkNotNull(env.linkRepository.findByDiscordId("discord-1"))

        assertEquals("alice", byIdentity.discordName)
        assertTrue(byIdentity.isBooster)
        assertEquals(id, byDiscord.id.value)
    }

    @Test
    fun `unknown links are not found`() {
        assertNull(env.linkRepository.findByIdentityId(Uuid.random()))
        assertNull(env.linkRepository.findByDiscordId("nobody"))
    }

    @Test
    fun `creating a second link for the same player is rejected`() {
        val id = Uuid.random()
        env.linkRepository.create(id, "discord-1", "alice", false)

        assertThrows(IllegalArgumentException::class.java) { env.linkRepository.create(id, "discord-2", "bob", false) }
    }

    @Test
    fun `a discord account can only be linked to one player`() {
        env.linkRepository.create(Uuid.random(), "discord-1", "alice", false)

        assertThrows(Exception::class.java) { env.linkRepository.create(Uuid.random(), "discord-1", "alice", false) }
    }

    @Test
    fun `updating a link changes its fields and its timestamp`() {
        val id = Uuid.random()
        env.linkRepository.create(id, "discord-1", "alice", false)
        env.ageLink(id, 1.minutes)
        val before = checkNotNull(env.linkRepository.findByIdentityId(id)).lastUpdatedAt

        env.linkRepository.update(id, "discord-1", "alice2", true)

        val after = checkNotNull(env.linkRepository.findByIdentityId(id))
        assertEquals("alice2", after.discordName)
        assertTrue(after.isBooster)
        assertTrue(after.lastUpdatedAt > before)
    }

    @Test
    fun `updating can clear the discord account`() {
        val id = Uuid.random()
        env.linkRepository.create(id, "discord-1", "alice", true)

        env.linkRepository.update(id, null, null, false)

        assertNull(env.linkRepository.findByDiscordId("discord-1"))
        assertNull(checkNotNull(env.linkRepository.findByIdentityId(id)).discordId)
    }

    @Test
    fun `updating an unknown link is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { env.linkRepository.update(Uuid.random(), "d", "n", false) }
    }

    @Test
    fun `findAll returns every link`() {
        assertTrue(env.linkRepository.findAll().isEmpty())
        env.linkRepository.create(Uuid.random(), "discord-1", "alice", false)
        env.linkRepository.create(Uuid.random(), "discord-2", "bob", false)

        assertEquals(setOf("alice", "bob"), env.linkRepository.findAll().map { it.discordName }.toSet())
    }

    // Link code repository

    @Test
    fun `a created code is valid for the validity duration`() {
        val id = Uuid.random()
        val before = Clock.System.now()

        val code = env.codeRepository.create(id)

        assertTrue(code.code.isNotBlank())
        assertTrue(code.validUntil >= before + LINK_CODE_VALIDITY_DURATION - 1.minutes)
        assertTrue(code.validUntil <= Clock.System.now() + LINK_CODE_VALIDITY_DURATION)
    }

    @Test
    fun `a code can be found by player and by code`() {
        val id = Uuid.random()
        val code = env.codeRepository.create(id)

        assertEquals(code.code, checkNotNull(env.codeRepository.findByEntityId(id)).code)
        assertEquals(id, checkNotNull(env.codeRepository.findByCode(code.code)).id.value)
        assertNull(env.codeRepository.findByCode("not-a-code"))
    }

    @Test
    fun `a player can only have one code at a time`() {
        val id = Uuid.random()
        env.codeRepository.create(id)

        assertThrows(IllegalStateException::class.java) { env.codeRepository.create(id) }
    }

    @Test
    fun `codes are unique per player`() {
        val first = env.codeRepository.create(Uuid.random())
        val second = env.codeRepository.create(Uuid.random())

        assertFalse(first.code == second.code)
    }

    @Test
    fun `deleting a code reports whether there was one`() {
        val id = Uuid.random()
        env.codeRepository.create(id)

        assertTrue(env.codeRepository.delete(id))
        assertFalse(env.codeRepository.delete(id))
        assertNull(env.codeRepository.findByEntityId(id))
    }

    @Test
    fun `deleteExpired only removes expired codes and counts them`() {
        val expiredOne = Uuid.random()
        val expiredTwo = Uuid.random()
        val valid = Uuid.random()
        listOf(expiredOne, expiredTwo, valid).forEach { env.codeRepository.create(it) }
        env.expireCode(expiredOne)
        env.expireCode(expiredTwo)

        assertEquals(2, env.codeRepository.deleteExpired())

        assertNotNull(env.codeRepository.findByEntityId(valid))
        assertNull(env.codeRepository.findByEntityId(expiredOne))
    }
}
