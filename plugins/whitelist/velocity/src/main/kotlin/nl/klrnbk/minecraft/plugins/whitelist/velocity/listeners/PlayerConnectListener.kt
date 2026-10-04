package nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.PlayerWhitelistFacade.JoinCheckResult
import org.slf4j.Logger
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerConnectListener
    @Inject
    constructor(
        private val playerWhitelistFacade: PlayerWhitelistFacade,
        private val logger: Logger,
    ) {
        // Last, so every other plugin (Identity registering the player, auth plugins setting the login mode) has had its say.
        @Suppress("DEPRECATION")
        @Subscribe(order = PostOrder.LAST)
        fun onPreLogin(event: PreLoginEvent) {
            // Someone else already denied the connection, keep their reason.
            if (!event.result.isAllowed) return

            // Fail closed: if the whitelist can't be checked (e.g. the database is down) nobody gets in.
            val check =
                try {
                    // UniqueId is only there for 1.20.2+ clients, older ones are checked by name.
                    playerWhitelistFacade.checkJoin(event.uniqueId?.toKotlinUuid(), event.username)
                } catch (exception: Exception) {
                    logger.error("Could not check the whitelist for player ${event.username} (${event.uniqueId}), denying the connection.", exception)
                    null
                }

            if (check?.isAllowed == true) return // leave the result alone so login modes set by other plugins survive

            logger.info("Denied ${event.username} (${event.uniqueId}): ${check ?: "whitelist check failed"}")
            event.result = PreLoginEvent.PreLoginComponentResult.denied(playerWhitelistFacade.getDenialMessage())
        }
    }
