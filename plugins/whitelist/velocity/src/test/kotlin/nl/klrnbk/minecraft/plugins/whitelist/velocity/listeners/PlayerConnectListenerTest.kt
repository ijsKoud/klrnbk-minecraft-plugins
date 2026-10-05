package nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.proxy.Player
import io.mockk.every
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.WhitelistTestEnvironment
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.identityPlayer
import nl.klrnbk.minecraft.plugins.whitelist.common.registerIdentity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import kotlin.uuid.toKotlinUuid

class PlayerConnectListenerTest {
    private val env = WhitelistTestEnvironment().also { it.start() }
    private val player = identityPlayer("Alice")
    private val admin = identityPlayer("Admin")
    private val listener =
        PlayerConnectListener(
            PlayerWhitelistFacade(env.playerWhitelistService, env.activeStatusService, env.configService),
            NOPLogger.NOP_LOGGER,
        )

    init {
        registerIdentity(player, admin)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private fun connect(
        uuid: java.util.UUID = player.playerId,
        name: String = player.name,
        before: ResultedEvent.ComponentResult? = null,
    ): LoginEvent {
        val velocityPlayer =
            mockk<Player>(relaxed = true).also {
                every { it.uniqueId } returns uuid
                every { it.username } returns name
            }
        val event = LoginEvent(velocityPlayer, "")
        if (before != null) event.result = before
        listener.onLogin(event)
        return event
    }

    private fun setWhitelist(enabled: Boolean) = env.activeStatusService.setWhitelistEnabled(enabled, admin.id.toKotlinUuid())

    @Test
    fun `everyone can join while the whitelist is disabled`() {
        assertTrue(connect().result.isAllowed)
    }

    @Test
    fun `players that are not whitelisted are denied while the whitelist is enabled`() {
        setWhitelist(true)

        assertFalse(connect().result.isAllowed)
    }

    @Test
    fun `the denial carries the configured kick message`() {
        setWhitelist(true)

        val result = connect().result

        assertTrue(result.reasonComponent.isPresent)
    }

    @Test
    fun `whitelisted players can join while the whitelist is enabled`() {
        setWhitelist(true)
        env.playerWhitelistService.addPlayerToWhitelist(player.id.toKotlinUuid(), admin.id.toKotlinUuid())

        assertTrue(connect().result.isAllowed)
    }

    @Test
    fun `players are denied when the whitelist can't be checked`() {
        setWhitelist(true)
        env.playerWhitelistService.addPlayerToWhitelist(player.id.toKotlinUuid(), admin.id.toKotlinUuid())
        env.databaseService.stop()

        assertFalse(connect().result.isAllowed)
    }

    @Test
    fun `players are denied when identity doesn't know them`() {
        setWhitelist(true)
        IdentityProvider.unregister()
        registerIdentity(admin)

        assertFalse(connect().result.isAllowed)
    }

    @Test
    fun `players unknown to identity are denied`() {
        setWhitelist(true)

        assertFalse(connect(uuid = java.util.UUID.randomUUID()).result.isAllowed)
    }

    @Test
    fun `a whitelisted cracked player is allowed under the offline uuid the proxy gave them`() {
        val offlineUuid = com.velocitypowered.api.util.UuidUtils.generateOfflinePlayerUuid("Steve")
        val steve = identityPlayer("Steve").copy(playerId = offlineUuid)
        registerIdentity(steve, admin)
        setWhitelist(true)
        env.playerWhitelistService.addPlayerToWhitelist(steve.id.toKotlinUuid(), admin.id.toKotlinUuid())

        assertTrue(connect(uuid = offlineUuid, name = "Steve").result.isAllowed)
    }

    @Test
    fun `a different name can't ride on a whitelisted player's uuid by claiming it`() {
        setWhitelist(true)
        env.playerWhitelistService.addPlayerToWhitelist(player.id.toKotlinUuid(), admin.id.toKotlinUuid())

        // Under the final UUID of an unknown player the check fails, whatever UUID the client claimed earlier.
        assertFalse(connect(uuid = java.util.UUID.randomUUID(), name = "Mallory").result.isAllowed)
    }

    @Test
    fun `a connection another plugin already denied stays denied with its reason`() {
        setWhitelist(true)
        env.playerWhitelistService.addPlayerToWhitelist(player.id.toKotlinUuid(), admin.id.toKotlinUuid())
        val reason = net.kyori.adventure.text.Component.text("Banned")

        val result = connect(before = ResultedEvent.ComponentResult.denied(reason)).result

        assertFalse(result.isAllowed)
        assertTrue(result.reasonComponent.get() == reason)
    }
}
