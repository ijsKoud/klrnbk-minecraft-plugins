package nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade
import org.slf4j.Logger
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerConnectListener
    @Inject
    constructor(
        private val playerWhitelistFacade: PlayerWhitelistFacade,
        private val logger: Logger,
    ) {
        @Subscribe
        fun onPreLogin(event: PreLoginEvent) {
            // UniqueId is guaranteed to exist at v1.20.2+, matching identity's own PreLoginEvent handling.
            val playerId = event.uniqueId!!.toKotlinUuid()

            // Fail closed: if the whitelist can't be checked (e.g. the database is down) nobody gets in.
            val allowed =
                try {
                    playerWhitelistFacade.isPlayerAllowedToJoinServer(playerId)
                } catch (exception: Exception) {
                    logger.error("Could not check the whitelist for player $playerId, denying the connection.", exception)
                    false
                }

            event.result =
                if (allowed) {
                    PreLoginEvent.PreLoginComponentResult.allowed()
                } else {
                    PreLoginEvent.PreLoginComponentResult.denied(playerWhitelistFacade.getDenialMessage())
                }
        }
    }
