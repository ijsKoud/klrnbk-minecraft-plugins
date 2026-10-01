package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerWhitelistFacade
    @Inject
    constructor(
        private val playerWhitelistService: PlayerWhitelistService,
        private val activeStatusService: ActiveStatusService,
        private val configService: ConfigService,
    ) {
        fun isPlayerWhitelisted(playerIdentityId: Uuid): Boolean = playerWhitelistService.isPlayerWhitelisted(playerIdentityId)

        fun isServerWhitelistActive(): Boolean = activeStatusService.isWhitelistEnabled()

        fun isPlayerAllowedToJoinServer(playerId: Uuid): Boolean {
            // Checked first so a disabled whitelist never depends on Identity or the database.
            if (isServerWhitelistActive().not()) return true

            val playerIdentityId =
                IdentityProvider.get().getPlayerFromUuid(playerId.toJavaUuid())?.id
                    ?: throw IllegalArgumentException("Player with id $playerId does not exist on Identity")

            return isPlayerWhitelisted(playerIdentityId.toKotlinUuid())
        }

        fun getDenialMessage(): Component {
            val message = configService.getConfig().kickMessage
            return MessageFactory.factory().miniMessage.deserialize(message)
        }
    }
