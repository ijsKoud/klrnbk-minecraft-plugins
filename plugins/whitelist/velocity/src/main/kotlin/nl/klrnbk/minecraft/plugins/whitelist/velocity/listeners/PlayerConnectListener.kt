package nl.klrnbk.minecraft.plugins.whitelist.velocity.listeners

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
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
        @Subscribe(order = PostOrder.LAST)
        fun onLogin(event: LoginEvent) {
            if (!event.result.isAllowed) return

            val check =
                try {
                    playerWhitelistFacade.checkJoin(event.player.uniqueId.toKotlinUuid(), event.player.username)
                } catch (exception: Exception) {
                    logger.error(
                        "Could not check the whitelist for player ${event.player.username} (${event.player.uniqueId}), denying the connection.",
                        exception,
                    )
                    null
                }

            if (check?.isAllowed == true) return

            logger.info("Denied ${event.player.username} (${event.player.uniqueId}): ${check ?: "whitelist check failed"}")
            event.result = ResultedEvent.ComponentResult.denied(playerWhitelistFacade.getDenialMessage())
        }
    }
