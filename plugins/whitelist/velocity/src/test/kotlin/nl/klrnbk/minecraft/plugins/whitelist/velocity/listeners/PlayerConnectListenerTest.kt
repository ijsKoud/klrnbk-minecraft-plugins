package nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners

import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.proxy.InboundConnection
import io.mockk.mockk
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.WhitelistTestEnvironment
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade
import nl.klrnbk.minecraft.plugins.whitelist.velocity.identityPlayer
import nl.klrnbk.minecraft.plugins.whitelist.velocity.registerIdentity
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

    private fun connect(): PreLoginEvent {
        val event = PreLoginEvent(mockk<InboundConnection>(relaxed = true), player.name, player.playerId)
        listener.onPreLogin(event)
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
}
