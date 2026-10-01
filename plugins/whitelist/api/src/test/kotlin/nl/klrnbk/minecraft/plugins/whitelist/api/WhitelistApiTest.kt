package nl.klrnbk.minecraft.plugins.whitelist.api

import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistLog
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistSettingsLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class WhitelistApiTest {
    private val api =
        object : WhitelistApi {
            override fun isPlayerWhitelisted(identityId: UUID) = false

            override fun isWhitelistEnabled() = false

            override fun setWhitelistEnabled(
                enabled: Boolean,
                actorIdentityId: UUID,
            ) = false

            override fun addPlayerToWhitelist(
                identityId: UUID,
                actorIdentityId: UUID,
            ) = false

            override fun removePlayerFromWhitelist(
                identityId: UUID,
                actorIdentityId: UUID,
            ) = false

            override fun getPlayerLogs(
                identityId: UUID,
                page: Int,
                itemsPerPage: Int,
            ): List<WhitelistLog> = emptyList()

            override fun getPlayerLogsCount(identityId: UUID) = 0L

            override fun getSettingsLogs(
                page: Int,
                itemsPerPage: Int,
            ): List<WhitelistSettingsLog> = emptyList()

            override fun getSettingsLogsCount() = 0L
        }

    @AfterEach
    fun cleanup() = WhitelistProvider.unregister()

    @Test
    fun `uses stable JDK UUID signatures`() {
        assertNotNull(WhitelistApi::class.java.getMethod("isPlayerWhitelisted", UUID::class.java))
        assertNotNull(WhitelistApi::class.java.getMethod("addPlayerToWhitelist", UUID::class.java, UUID::class.java))
        assertNotNull(WhitelistApi::class.java.getMethod("removePlayerFromWhitelist", UUID::class.java, UUID::class.java))
        assertNotNull(
            WhitelistApi::class.java.getMethod(
                "getPlayerLogs",
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
        )
    }

    @Test
    fun `provider throws before an api is registered`() {
        WhitelistProvider.unregister()
        assertThrows(IllegalStateException::class.java) { WhitelistProvider.get() }
    }

    @Test
    fun `provider returns the registered api until it is unregistered`() {
        WhitelistProvider.register(api)
        assertSame(api, WhitelistProvider.get())

        WhitelistProvider.unregister()
        assertThrows(IllegalStateException::class.java) { WhitelistProvider.get() }
    }

    @Test
    fun `console actor id is the nil uuid`() {
        assertEquals(UUID(0, 0), WhitelistApi.CONSOLE_ACTOR_ID)
    }
}
